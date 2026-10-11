package com.focusrealm.everdeep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class FocusVpn : VpnService() {

    companion object {
        const val ACTION_STOP = "everdeep.vpn.stop"
        private val RELAY = arrayOf("8.8.8.8", "8.8.4.4")
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val main = Handler(Looper.getMainLooper())
    private var clearRun: Runnable? = null
    private val writeLock = Any()
    private val relayPool = java.util.concurrent.Executors.newCachedThreadPool()
    private var relayIdx = 0
    @Volatile private var running = false

    override fun onCreate() { super.onCreate(); FocusBus.vpnActive = true }
    override fun onRevoke() { FocusBus.vpnActive = false; stopVpn(); super.onRevoke() }
    override fun onDestroy() { FocusBus.vpnActive = false; stopVpn(); super.onDestroy() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (tun != null) return
        tun = try {
            Builder()
                .setSession("Everdeep site filter")
                .setMtu(1500)
                .addAddress("10.111.0.1", 32)
                .addDnsServer("10.111.0.2")
                .addRoute("10.111.0.0", 24)
                .addRoute("8.8.8.8", 32).addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32).addRoute("1.0.0.1", 32)
                .addRoute("9.9.9.9", 32)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) { null }
        if (tun == null) { stopSelf(); return }
        startFgs()
        running = true
        worker = Thread { loop() }.apply { start() }
    }

    private fun startFgs() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("vpn", "Site filter", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "vpn")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Everdeep site filter")
            .setContentText("🌐 blocking ${FocusBus.siteBlocklist.value.size} domains")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 13, n, type)
    }

    private fun stopVpn() {
        running = false
        clearRun?.let { main.removeCallbacks(it) }; clearRun = null
        try { worker?.join(500) } catch (_: InterruptedException) {}
        worker = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        FocusBus.siteHit.value = null
        FocusBus.onSiteHit?.invoke()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun loop() {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (_: Exception) { break }
            if (n >= 20) handle(buf, n, output)
        }
        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    private fun handle(b: ByteArray, n: Int, out: FileOutputStream) {
        if ((b[0].toInt() shr 4) != 4 || b[9] != 17.toByte()) return
        val ihl = (b[0].toInt() and 0xf) * 4
        if (n < ihl + 8) return
        val src = b.copyOfRange(12, 16); val dst = b.copyOfRange(16, 20)
        val sport = ((b[ihl].toInt() and 0xff) shl 8) or (b[ihl + 1].toInt() and 0xff)
        val dport = ((b[ihl + 2].toInt() and 0xff) shl 8) or (b[ihl + 3].toInt() and 0xff)
        if (dport != 53) return
        val doff = ihl + 8
        if (n < doff + 12 || (b[doff + 2].toInt() and 0x80) != 0) return

        var p = doff + 12
        val sb = StringBuilder()
        while (p < n) {
            val l = b[p].toInt() and 0xff
            if (l == 0) { p++; break }
            if (p + 1 + l + 4 > n) return
            for (i in 1..l) sb.append(b[p + i].toInt().toChar())
            sb.append('.')
            p += 1 + l
        }
        if (sb.isEmpty() || p + 4 > n) return
        val domain = sb.toString().trimEnd('.').lowercase()
        val qlen = (p + 4) - (doff + 12)

        if (FocusBus.siteMatches(domain)) {
            synchronized(writeLock) { out.write(blockedResponse(b, doff, qlen, src, dst, sport)) }
            hit(domain)
            return
        }
        val q = b.copyOf(n)
        relayPool.execute { relay(q, doff, n, src, dst, sport, out) }
    }

    /** a hit freezes the sim until TTL elapses after the LAST matching query */
    private fun hit(domain: String) {
        FocusBus.notifySiteHit(domain)
        clearRun?.let { main.removeCallbacks(it) }
        clearRun = Runnable {
            if (FocusBus.siteHit.value != null) {
                FocusBus.siteHit.value = null
                FocusBus.onSiteClear?.invoke()
            }
        }
        main.postDelayed(clearRun!!, 30_000L)
    }

    private fun blockedResponse(q: ByteArray, doff: Int, qlen: Int,
                                src: ByteArray, dst: ByteArray, sport: Int): ByteArray {
        val total = 20 + 8 + 12 + qlen + 16
        val r = ByteArray(total)
        r[0] = 0x45; r[2] = (total shr 8).toByte(); r[3] = total.toByte()
        r[6] = 0x40; r[8] = 64; r[9] = 17
        dst.copyInto(r, 12)
        src.copyInto(r, 16)
        r[21] = 53
        r[22] = (sport shr 8).toByte(); r[23] = sport.toByte()
        val udpLen = total - 20
        r[24] = (udpLen shr 8).toByte(); r[25] = udpLen.toByte()
        q.copyInto(r, 28, doff, doff + 12 + qlen)
        r[30] = 0x81.toByte(); r[31] = 0x80.toByte()
        r[34] = 1
        var o = 28 + 12 + qlen
        r[o] = 0xC0.toByte(); r[o + 1] = 0x0C
        r[o + 2] = 0; r[o + 3] = 1
        r[o + 4] = 0; r[o + 5] = 1
        r[o + 9] = 10
        r[o + 11] = 4
        val c = csum(r, 0, 20)
        r[10] = (c shr 8).toByte(); r[11] = c.toByte()
        return r
    }

    private fun relay(q: ByteArray, doff: Int, n: Int, src: ByteArray, dst: ByteArray,
                      sport: Int, out: FileOutputStream) {
        val dns = q.copyOfRange(doff, n)
        val sock = DatagramSocket()
        try {
            protect(sock)
            sock.soTimeout = 3000
            val addr = InetAddress.getByName(RELAY[relayIdx and 1]); relayIdx++
            sock.send(DatagramPacket(dns, dns.size, addr, 53))
            val rb = ByteArray(2048)
            val rp = DatagramPacket(rb, rb.size)
            sock.receive(rp)
            synchronized(writeLock) {
                out.write(wrapResponse(rb.copyOfRange(0, rp.length), dst, src, sport))
            }
        } catch (_: Exception) {
        } finally { sock.close() }
    }

    private fun wrapResponse(dns: ByteArray, from: ByteArray, to: ByteArray, dport: Int): ByteArray {
        val total = 20 + 8 + dns.size
        val r = ByteArray(total)
        r[0] = 0x45; r[2] = (total shr 8).toByte(); r[3] = total.toByte()
        r[6] = 0x40; r[8] = 64; r[9] = 17
        from.copyInto(r, 12); to.copyInto(r, 16)
        r[21] = 53
        r[22] = (dport shr 8).toByte(); r[23] = dport.toByte()
        val udpLen = total - 20
        r[24] = (udpLen shr 8).toByte(); r[25] = udpLen.toByte()
        dns.copyInto(r, 28)
        val c = csum(r, 0, 20)
        r[10] = (c shr 8).toByte(); r[11] = c.toByte()
        return r
    }

    private fun csum(seg: ByteArray, off: Int, len: Int): Int {
        var s = 0; var i = 0
        while (i + 1 < len) {
            s += ((seg[off + i].toInt() and 0xff) shl 8) or (seg[off + i + 1].toInt() and 0xff)
            i += 2
        }
        if (i < len) s += (seg[off + i].toInt() and 0xff) shl 8
        while (s shr 16 != 0) s = (s and 0xffff) + (s shr 16)
        return s.inv() and 0xffff
    }
}
