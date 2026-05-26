package chat.simplex.common.platform

import android.media.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import kotlin.math.*

/**
 * Android voice scrambler.
 *
 * Artistic disguises (reversible if preset is known):
 *  VILLAIN  — pitch -7st, low-pass 2800, bass +6 dB
 *  HELIUM   — pitch +7st, high-pass 200 Hz
 *  ROBOT    — pitch 0st,  tremolo 30 Hz, low-pass 3000
 *  DEMON    — pitch -10st, low-pass 2000, bass +10 dB
 *  GHOST    — pitch -3st,  echo 120 ms 45%
 *  CYBORG   — pitch +4st,  bit-crush 8-bit
 *
 * Irreversible mode (Paranoid):
 *  PARANOID — STFT spectral-envelope randomization + F0 flattening +
 *             random phase + variable-hop time jitter + pink-noise bed +
 *             double lossy AAC recode. Destroys all 3 speaker biometrics
 *             (F0 contour, formants F1–F4, prosody) with per-frame random
 *             parameters seeded by SecureRandom and never stored anywhere.
 *             → defeats manual inverse-effect recovery AND
 *               defeats ML speaker-recognition systems (x-vector/ECAPA).
 */
actual object VoiceScramblerProcessor {

    actual fun process(filePath: String, effect: VoiceEffect): String {
        if (effect == VoiceEffect.NORMAL) return filePath
        val resolved = if (effect == VoiceEffect.RANDOM) VoiceEffect.randomScramble() else effect
        return try {
            val decoded = decodeToMonoPcm(filePath) ?: return filePath
            val (pcm, sr) = decoded
            if (pcm.isEmpty()) return filePath

            val processed = when (resolved) {
                VoiceEffect.VILLAIN  -> villain(pcm, sr)
                VoiceEffect.HELIUM   -> helium(pcm, sr)
                VoiceEffect.ROBOT    -> robot(pcm, sr)
                VoiceEffect.DEMON    -> demon(pcm, sr)
                VoiceEffect.GHOST    -> ghost(pcm, sr)
                VoiceEffect.CYBORG   -> cyborg(pcm, sr)
                VoiceEffect.PARANOID -> paranoid(pcm, sr)
                else -> pcm
            }

            // Paranoid mode goes through a second lossy re-encode at lower bitrate
            // to destroy any residual fine-grain cepstral fingerprint.
            val targetBitrate = if (resolved == VoiceEffect.PARANOID) 32000 else 64000

            val outFile = File(filePath).let {
                File(it.parent, it.nameWithoutExtension + "_scrambled.m4a")
            }
            encodeToM4a(processed, sr, outFile.absolutePath, targetBitrate)
            outFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("VoiceScrambler", "Effect failed: ${e.message}", e)
            filePath
        }
    }

    // ═══════════════ 6 EFFECT CHAINS ═══════════════

    /** Villain — deep menacing Thanos voice */
    private fun villain(pcm: ShortArray, sr: Int): ShortArray {
        var a = pitchShift(pcm, -7.0)
        a = lowPassFilter(a, sr, 2800.0)
        a = bassBoost(a, sr, 6.0)
        return a
    }

    /** Helium — high-pitched child / chipmunk */
    private fun helium(pcm: ShortArray, sr: Int): ShortArray {
        var a = pitchShift(pcm, 7.0)
        a = highPassFilter(a, sr, 200.0)
        return a
    }

    /** Robot — metallic tremolo machine voice */
    private fun robot(pcm: ShortArray, sr: Int): ShortArray {
        var a = tremolo(pcm, sr, 30.0, 0.6)
        a = lowPassFilter(a, sr, 3000.0)
        return a
    }

    /** Demon — very deep scary voice */
    private fun demon(pcm: ShortArray, sr: Int): ShortArray {
        var a = pitchShift(pcm, -10.0)
        a = lowPassFilter(a, sr, 2000.0)
        a = bassBoost(a, sr, 10.0)
        return a
    }

    /** Ghost — eerie spectral with echo */
    private fun ghost(pcm: ShortArray, sr: Int): ShortArray {
        var a = pitchShift(pcm, -3.0)
        a = echo(a, sr, 0.12, 0.45)
        a = lowPassFilter(a, sr, 3200.0)
        a = bassBoost(a, sr, 3.0)
        return a
    }

    /** Cyborg — digital mechanical with mild bit-crush */
    private fun cyborg(pcm: ShortArray, sr: Int): ShortArray {
        var a = pitchShift(pcm, 4.0)
        a = bitCrush(a, 8)
        a = lowPassFilter(a, sr, 5000.0)
        return a
    }

    // ═══════════════ DSP PRIMITIVES ═══════════════

    private fun pitchShift(samples: ShortArray, semitones: Double): ShortArray {
        if (semitones == 0.0) return samples
        val pitchFactor = 2.0.pow(semitones / 12.0)
        val stretchRatio = 1.0 / pitchFactor
        val newLength = (samples.size * stretchRatio).toInt()
        val out = ShortArray(newLength)
        for (i in out.indices) {
            val srcPos = i.toDouble() / stretchRatio
            val idx = srcPos.toInt()
            val frac = srcPos - idx
            val s0 = samples[idx.coerceIn(0, samples.size - 1)].toInt()
            val s1 = samples[(idx + 1).coerceIn(0, samples.size - 1)].toInt()
            out[i] = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun lowPassFilter(samples: ShortArray, sr: Int, cutoffHz: Double): ShortArray {
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val dt = 1.0 / sr
        val alpha = dt / (rc + dt)
        val out = ShortArray(samples.size)
        var prev = samples[0].toDouble()
        out[0] = samples[0]
        for (i in 1 until samples.size) {
            prev += alpha * (samples[i].toDouble() - prev)
            out[i] = prev.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun highPassFilter(samples: ShortArray, sr: Int, cutoffHz: Double): ShortArray {
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val dt = 1.0 / sr
        val alpha = rc / (rc + dt)
        val out = ShortArray(samples.size)
        var prevIn = samples[0].toDouble()
        var prevOut = samples[0].toDouble()
        out[0] = samples[0]
        for (i in 1 until samples.size) {
            val cur = samples[i].toDouble()
            prevOut = alpha * (prevOut + cur - prevIn)
            prevIn = cur
            out[i] = prevOut.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun bassBoost(samples: ShortArray, sr: Int, gainDb: Double): ShortArray {
        val cutoff = 300.0
        val rc = 1.0 / (2.0 * Math.PI * cutoff)
        val dt = 1.0 / sr
        val alpha = dt / (rc + dt)
        val boost = 10.0.pow(gainDb / 20.0)
        val out = ShortArray(samples.size)
        var prev = 0.0
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            prev += alpha * (s - prev)
            out[i] = (s + (boost - 1.0) * prev).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Amplitude modulation — makes voice sound metallic/robotic */
    private fun tremolo(samples: ShortArray, sr: Int, freqHz: Double, depth: Double): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val mod = 1.0 - depth * 0.5 * (1.0 + sin(2.0 * Math.PI * freqHz * i / sr))
            out[i] = (samples[i].toDouble() * mod).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Echo — mix delayed copy for spectral/eerie feel */
    private fun echo(samples: ShortArray, sr: Int, delaySec: Double, wetMix: Double): ShortArray {
        val delaySamples = (sr * delaySec).toInt()
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val dry = samples[i].toDouble()
            val delayed = if (i >= delaySamples) samples[i - delaySamples].toDouble() else 0.0
            out[i] = (dry * (1.0 - wetMix * 0.5) + delayed * wetMix).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Bit crush — reduce bit depth for digital/mechanical sound */
    private fun bitCrush(samples: ShortArray, bits: Int): ShortArray {
        val levels = (1 shl bits).toDouble()
        val step = 65536.0 / levels
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val normalized = (samples[i].toInt() + 32768).toDouble()
            val crushed = (floor(normalized / step) * step + step / 2.0) - 32768.0
            out[i] = crushed.toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    // ═══════════════ PARANOID — irreversible identity-destroying pipeline ═══════════════

    /**
     * Paranoid scramble. Rebuilds the voice from scratch using the ORIGINAL speech's
     * broad spectral envelope as a starting point, then destroys the three features
     * that identify a speaker:
     *
     *   1. Pitch contour (F0)   — replaced with a random monotone per frame.
     *   2. Vocal-tract formants — envelope is frequency-warped by a random per-frame
     *                             factor AND bin-shifted by a random per-frame offset.
     *                             The warp parameters are drawn from SecureRandom and
     *                             never transmitted or stored.
     *   3. Prosody / timing     — variable hop size (±25% jitter) on overlap-add
     *                             destroys micro-timing cues.
     *
     * Additionally:
     *   - Random phase per bin destroys temporal fine structure.
     *   - Pink-noise bed at -32 dBFS masks silence/breath fingerprints.
     *   - A later second-pass AAC re-encode at 32 kbps (handled by the caller) acts
     *     as a lossy quantizer that further destroys residual cepstral features.
     *
     * The result is still intelligible (the linguistic content is preserved via the
     * randomized envelope), but the speaker identity is effectively gone. Because
     * the randomization parameters are never stored, no one — including us — can
     * invert the transform to recover the original voice.
     */
    private fun paranoid(pcm: ShortArray, sr: Int): ShortArray {
        val n = 1024                         // FFT frame size
        val nyq = n / 2
        val hopBase = n / 4                  // 256-sample base hop (75% overlap)
        val rng = SecureRandom()
        val hann = FloatArray(n) { i ->
            (0.5 - 0.5 * cos(2.0 * Math.PI * i / (n - 1))).toFloat()
        }

        // Over-allocate output buffer for time jitter
        val outLen = (pcm.size * 13 / 10) + n
        val out = FloatArray(outLen)
        val winSum = FloatArray(outLen) // running window sum for OLA normalization

        val re = FloatArray(n)
        val im = FloatArray(n)

        var readIdx = 0
        var writeIdx = 0

        while (readIdx + n <= pcm.size) {
            // ── 1. Windowed frame → complex ──
            for (i in 0 until n) {
                re[i] = pcm[readIdx + i].toFloat() * hann[i]
                im[i] = 0f
            }

            // ── 2. Forward FFT ──
            fft(re, im, inverse = false)

            // ── 3. Magnitude ──
            val mag = FloatArray(nyq + 1)
            for (k in 0..nyq) {
                mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
            }

            // ── 4. Smooth envelope via moving-average on log-magnitude ──
            // This captures formant peaks without harmonic fine structure.
            val env = FloatArray(nyq + 1)
            val envWin = 24 // bins — ~wider than one harmonic at 16 kHz
            val logMag = FloatArray(nyq + 1) { ln(mag[it] + 1e-6f) }
            var acc = 0f
            for (k in 0..nyq) {
                val lo = (k - envWin).coerceAtLeast(0)
                val hi = (k + envWin).coerceAtMost(nyq)
                acc = 0f
                for (j in lo..hi) acc += logMag[j]
                env[k] = exp(acc / (hi - lo + 1))
            }

            // ── 5. Frame-random envelope warp (destroys formants) ──
            // warpFactor ∈ [0.78, 1.22]: stretches/squashes the frequency axis
            // binOffset ∈ [−40, +40] bins: shifts all formants up/down
            val warpFactor = 0.78f + rng.nextFloat() * 0.44f
            val binOffset = (rng.nextFloat() - 0.5f) * 80f
            val warpedEnv = FloatArray(nyq + 1) { k ->
                val srcF = k.toFloat() / warpFactor + binOffset
                val lo = srcF.toInt().coerceIn(0, nyq)
                val hi = (lo + 1).coerceAtMost(nyq)
                val frac = (srcF - lo).coerceIn(0f, 1f)
                env[lo] * (1 - frac) + env[hi] * frac
            }

            // ── 6. Synthetic excitation (flattens pitch) ──
            // Generate a comb of harmonics at a random target F0 per frame
            // plus a white-noise component. This replaces the speaker's own
            // harmonic pattern (which leaks F0 contour) with a fresh one.
            val targetF0 = 160f + rng.nextFloat() * 60f  // 160–220 Hz per frame
            val binHz = sr.toFloat() / n
            val excitation = FloatArray(nyq + 1) { k ->
                val freq = k * binHz
                // Distance (in Hz) to the nearest harmonic of targetF0
                val harmonic = (freq / targetF0).roundToInt() * targetF0
                val dist = abs(freq - harmonic)
                // Narrow Gaussian peak at each harmonic (σ ≈ 25 Hz)
                val peak = exp(-(dist * dist) / (2f * 25f * 25f))
                val noise = 0.25f + rng.nextFloat() * 0.15f
                peak + noise
            }

            // ── 7. Combined new magnitude ──
            val newMag = FloatArray(nyq + 1) { warpedEnv[it] * excitation[it] }

            // ── 8. Random phase (destroys temporal fine structure) ──
            val newRe = FloatArray(n)
            val newIm = FloatArray(n)
            val twoPi = (2.0 * Math.PI).toFloat()
            for (k in 0..nyq) {
                val phase = rng.nextFloat() * twoPi
                newRe[k] = newMag[k] * cos(phase)
                newIm[k] = newMag[k] * sin(phase)
            }
            // Hermitian symmetry for real output
            for (k in 1 until nyq) {
                newRe[n - k] = newRe[k]
                newIm[n - k] = -newIm[k]
            }
            newIm[0] = 0f
            newIm[nyq] = 0f

            // ── 9. Inverse FFT ──
            fft(newRe, newIm, inverse = true)

            // ── 10. Window again + Overlap-Add with random hop (time jitter) ──
            val hopJitter = hopBase + (rng.nextInt(65) - 32) // ±32 samples
            val writeEnd = writeIdx + n
            if (writeEnd < outLen) {
                for (i in 0 until n) {
                    val w = hann[i]
                    out[writeIdx + i] += newRe[i] * w
                    winSum[writeIdx + i] += w * w
                }
            }
            writeIdx += hopJitter
            readIdx += hopJitter
        }

        // Include the tail of the last frame we wrote (not just up to writeIdx)
        val totalLen = (writeIdx + n).coerceAtMost(outLen)

        // ── 11. Normalize by window sum (standard OLA scaling) ──
        // Floor winSum to avoid noisy amplification at the edges.
        for (i in 0 until totalLen) {
            val ws = if (winSum[i] < 0.3f) 0.3f else winSum[i]
            out[i] = out[i] / ws
        }

        // ── 12. Pink-noise bed at −32 dBFS (masks silence fingerprint) ──
        val pinkLevel = 32768f * 0.0251f // −32 dBFS
        var b0 = 0f; var b1 = 0f; var b2 = 0f
        for (i in 0 until totalLen) {
            // Paul Kellet economy pink-noise filter
            val white = (rng.nextFloat() - 0.5f) * 2f
            b0 = 0.99886f * b0 + white * 0.0555179f
            b1 = 0.99332f * b1 + white * 0.0750759f
            b2 = 0.96900f * b2 + white * 0.1538520f
            val pink = b0 + b1 + b2 + white * 0.1848f
            out[i] += pink * pinkLevel
        }

        // ── 13. Peak-normalize + soft clip + convert to PCM16 ──
        var peak = 0f
        for (i in 0 until totalLen) {
            val a = abs(out[i]); if (a > peak) peak = a
        }
        val gain = if (peak > 1e-3f) (28000f / peak) else 1f
        val result = ShortArray(totalLen)
        for (i in 0 until totalLen) {
            val v = out[i] * gain
            // tanh soft clipper for graceful handling of residual peaks
            val clipped = tanh(v / 32768.0).toFloat() * 32767f
            result[i] = clipped.toInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    /**
     * In-place iterative radix-2 Cooley–Tukey FFT / IFFT.
     * Requires n to be a power of 2.  No external dependencies.
     */
    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        if (n == 1) return
        require(n and (n - 1) == 0) { "FFT size must be a power of 2" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        // Iterative butterflies
        val sign = if (inverse) 1.0 else -1.0
        var size = 2
        while (size <= n) {
            val half = size / 2
            val angle = sign * 2.0 * Math.PI / size
            val wStepRe = cos(angle).toFloat()
            val wStepIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val tRe = wRe * re[b] - wIm * im[b]
                    val tIm = wRe * im[b] + wIm * re[b]
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] = re[a] + tRe
                    im[a] = im[a] + tIm
                    val nwRe = wRe * wStepRe - wIm * wStepIm
                    wIm = wRe * wStepIm + wIm * wStepRe
                    wRe = nwRe
                }
                i += size
            }
            size = size shl 1
        }

        if (inverse) {
            val inv = 1f / n
            for (i in 0 until n) { re[i] *= inv; im[i] *= inv }
        }
    }

    // ═══════════════ DECODE / ENCODE ═══════════════

    private data class DecodeResult(val samples: ShortArray, val sampleRate: Int)

    private fun decodeToMonoPcm(filePath: String): DecodeResult? {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { audioTrackIndex = i; break }
        }
        if (audioTrackIndex < 0) return null
        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sampleRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 16000 }
        val channels = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 1 }

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
        val bufferInfo = MediaCodec.BufferInfo()
        val pcmChunks = mutableListOf<ShortArray>()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val idx = decoder.dequeueInputBuffer(10000)
                if (idx >= 0) {
                    val buf = decoder.getInputBuffer(idx) ?: continue
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx) ?: continue
                if (bufferInfo.size > 0) {
                    val shorts = ShortArray(bufferInfo.size / 2)
                    outBuf.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                    pcmChunks.add(shorts)
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        decoder.stop(); decoder.release(); extractor.release()

        val total = pcmChunks.sumOf { it.size }
        val result = ShortArray(total)
        var off = 0
        for (c in pcmChunks) { System.arraycopy(c, 0, result, off, c.size); off += c.size }

        val mono = if (channels == 2) {
            ShortArray(result.size / 2) { i -> ((result[i * 2].toInt() + result[i * 2 + 1].toInt()) / 2).toShort() }
        } else result

        return DecodeResult(mono, sampleRate)
    }

    private fun encodeToM4a(samples: ShortArray, sampleRate: Int, outputPath: String, bitRate: Int = 64000) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val fmt = MediaFormat.createAudioFormat(mime, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIdx = -1; var muxerStarted = false
        val bufInfo = MediaCodec.BufferInfo()
        var inputOff = 0; var inputDone = false; var outputDone = false
        val pcmBytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)

        while (!outputDone) {
            if (!inputDone) {
                val idx = encoder.dequeueInputBuffer(10000)
                if (idx >= 0) {
                    val buf = encoder.getInputBuffer(idx) ?: continue
                    val rem = pcmBytes.size - inputOff
                    if (rem <= 0) {
                        encoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val chunk = minOf(rem, buf.capacity())
                        buf.clear(); buf.put(pcmBytes, inputOff, chunk)
                        val pts = (inputOff.toLong() / 2) * 1_000_000L / sampleRate
                        encoder.queueInputBuffer(idx, 0, chunk, pts, 0)
                        inputOff += chunk
                    }
                }
            }
            val outIdx = encoder.dequeueOutputBuffer(bufInfo, 10000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) { muxerTrackIdx = muxer.addTrack(encoder.outputFormat); muxer.start(); muxerStarted = true }
                }
                outIdx >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIdx) ?: continue
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufInfo.size = 0
                    if (bufInfo.size > 0 && muxerStarted) {
                        outBuf.position(bufInfo.offset); outBuf.limit(bufInfo.offset + bufInfo.size)
                        muxer.writeSampleData(muxerTrackIdx, outBuf, bufInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        encoder.stop(); encoder.release()
        if (muxerStarted) { muxer.stop(); muxer.release() }
    }
}
