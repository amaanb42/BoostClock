# Offline alarm voice commands: implementation status

Last updated: 2026-08-05

Status legend:

- `[x]` implemented in the current worktree
- `[~]` partially implemented or not yet fully verified
- `[ ]` not implemented

## Current status

The feature is now an **integrated, build-verified prototype awaiting informal device testing**. It bundles sherpa-onnx 1.13.4 and quantized Moonshine v2 Tiny English (2026-02-27), uses an adaptive energy endpoint detector, and performs offline inference on a dedicated worker. Core debug and release APKs compile and package the pinned artifacts, but native loading and recognition have not yet been exercised on a physical device.

For the current prototype goal, the next milestone is a physical-device smoke test: enable voice control, ring an alarm, and confirm that “snooze” and “stop” work with the alarm both silent and audible. The corpus, latency, thermal, accessibility, and full release-flavor gates below are release qualification work, not blockers for iterating on the prototype.

## Model qualification

- [x] Define the common `VoiceRecognitionEngine`, `RecognitionState`, and `RecognitionHypothesis` boundary.
- [x] Document the candidate models, selection order, corpus, hard gates, and promotion record.
- [x] Create a non-shipping executable benchmark harness using the common engine interface.
- [~] Add a Moonshine Tiny English/sherpa-onnx benchmark adapter. The runner protocol exists; the pinned native runner does not.
- [~] Add a Whisper Tiny English/whisper.cpp benchmark adapter. The runner protocol exists; the pinned native runner does not.
- [~] Add a sherpa-onnx English Zipformer keyword-spotter benchmark adapter. The runner protocol exists; the pinned native runner does not.
- [ ] Assemble recordings from five speakers for both commands.
- [ ] Test normal speech in quiet and over tonal, music, and speech-containing alarms.
- [ ] Test whispered speech in quiet at approximately 0.3 m and 1 m.
- [ ] Assemble held-out normal and whispered timer-like phrases for WER evaluation.
- [ ] Assemble at least two hours of negative alarm, music, conversation, and room-noise audio.
- [ ] Run the corpus on representative arm64 Android 12, 14, and 16 devices.
- [ ] Include locked-screen runs and multiple custom alarm sounds.
- [ ] Record cold readiness, p95 action latency, thermal state, and installed-size growth.
- [ ] Select the winning candidate according to the required ordering.
- [~] Pin the prototype runtime, weights, decoding parameters, licenses, and SHA-256 hashes. Formal promotion is still outstanding.

### Candidates

- Quantized Moonshine Tiny English through sherpa-onnx.
- Quantized Whisper Tiny English through whisper.cpp.
- sherpa-onnx English Zipformer keyword spotter, used only if neither full-ASR candidate passes every gate.

### Hard gates

- Normal commands over alarm playback: intent recall >= 95%.
- Whispered commands in quiet: intent recall >= 90%.
- Negative corpus: zero false actions.
- Full-ASR held-out phrases: normal WER <= 10% and whispered WER <= 20%.
- Cold readiness <= 5 seconds.
- p95 action latency <= 2 seconds after utterance completion.
- No severe thermal state during a five-minute alarm session.
- Added installed size <= 150 MB.
- Runtime and weights must be permissively licensed and redistributable with verified checksums.

Select a passing full-ASR model by whispered recall, normal recall, latency, then size. Use the keyword spotter only if neither full-ASR candidate passes every gate.

## Runtime and audio implementation

- [x] Add the runtime-neutral recognition abstractions.
- [x] Add transient `AudioRecord` capture using 16 kHz mono PCM and `VOICE_RECOGNITION`.
- [x] Attempt to enable available echo cancellation, noise suppression, and automatic gain control.
- [x] Keep capture in bounded in-memory chunks without file or network output.
- [x] Warm the engine asynchronously from `AlarmService` without opening the microphone.
- [x] Start microphone capture from `AlarmActivity` only while it is resumed.
- [x] Stop capture on pause, voice/touch action, destruction, and before the custom snooze picker.
- [x] Leave alarm playback and alarm volume unchanged while listening.
- [x] Accept only complete final hypotheses that normalize exactly to `snooze` or `stop`.
- [x] Reject partial, embedded, and negated phrases at the command-normalization layer.
- [x] Add an atomic action gate for touch/voice races and repeated callbacks.
- [x] Make spoken stop use the existing alarm-dismiss path.
- [x] Make spoken snooze use the persisted snooze duration and bypass the picker.
- [x] Reject stale generations after pause, replacement, shutdown, and late or duplicate engine callbacks.
- [~] Implement the prototype Moonshine `VoiceRecognitionEngine`; it is not formally qualified.
- [x] Add adaptive energy-based endpoint segmentation for the prototype.
- [x] Enforce a six-second bounded utterance duration inside the recognition pipeline.
- [x] Perform actual model inference on a dedicated background worker.
- [~] Keep the engine confidence at `1.0f` because sherpa-onnx Moonshine does not expose utterance confidence; no benchmarked threshold exists.
- [~] Disable sherpa-onnx debug logging and avoid application transcript/audio logging; release auditing remains outstanding.
- [x] Add corrupt/missing-model detection and checksum validation before native initialization.
- [~] Add ordered cleanup for initialization, capture, inference, and native resources; physical failure injection remains outstanding.
- [x] Surface asynchronous microphone-read and native-inference failures instead of leaving the UI stuck in “Listening.”
- [x] Reject late asynchronous failures from an older listening generation.

## Packaging and permissions

- [x] Add `RECORD_AUDIO`.
- [x] Do not add network permission or a microphone foreground-service type.
- [x] Bundle sherpa-onnx 1.13.4 in core, foss, and gplay.
- [x] Bundle quantized Moonshine v2 Tiny English in core, foss, and gplay.
- [x] Add complete runtime and model license notices.
- [x] Package ONNX assets without compression and use the AAR native-library layout.
- [x] Add required R8/ProGuard keep rules.
- [x] Add build-time SHA-256 verification for every model and native artifact.
- [~] Keep packaged APK size within the 150 MB gate. Foss debug with both arm64 and x86_64 prototype runtimes is 142,035,994 bytes; installed-size growth is not yet measured.
- [~] Run release shrinking and native-load checks for every flavor. The core release shrink build passed with the earlier arm64-only prototype; physical-device native loading and foss/gplay builds remain outstanding. Debug prototypes now also retain x86_64 libraries for emulator smoke testing.

## Settings and alarm UI

- [x] Add the “Voice control” setting with a local-processing explanation; default is off.
- [x] Request microphone permission before persisting the enabled state.
- [x] Handle ordinary denial without enabling voice control.
- [x] Route a permanently denied user to application permission settings.
- [x] Disable voice control after later permission revocation without affecting the alarm.
- [x] Gate support on Android 12+, a bundled 64-bit runtime (arm64 device or x86_64 emulator), and non-low-RAM devices.
- [x] Show the unsupported-device requirements in settings.
- [x] Add a microphone icon and loading, listening, recognized, and unavailable strings.
- [x] Keep touch controls operational when voice initialization or capture fails.
- [~] Validate all permission paths across activity recreation and return from system settings.
- [x] Enable the setting only when the bundled prototype and device capabilities are present.
- [ ] Validate layout, accessibility, and translations for the new setting and alarm states.
- [ ] Verify full-screen/keyguard behavior on devices; no hidden/background capture may be added as a workaround.

## Tests and verification

- [x] Add basic unit tests for strict command normalization.
- [x] Add basic unit tests for duplicate, stale-generation, and touch/voice action gating.
- [x] Correct and compile-verify the `AudioRecordVoiceCapture.start()` `Result<Unit>` inference.
- [x] Add fake-engine session tests.
- [x] Add an explicit saved-duration voice-snooze test for both `useSameSnooze` states.
- [x] Add duplicate-engine-callback and late-callback session tests.
- [x] Add alarm-replacement and shutdown generation unit tests; device instrumentation remains outstanding below.
- [x] Add capability tests for supported and unsupported OS, ABI, low-RAM, and missing-model combinations.
- [x] Add permission policy tests for granted, denied, permanently denied, and revoked states.
- [x] Add build-time corrupt/missing-model verification; runtime failure injection remains outstanding.
- [ ] Instrument activity recreation, keyguard display, and notification-open fallback.
- [ ] Instrument custom snooze picker, alarm replacement, auto-dismiss, and model/capture failures.
- [ ] Verify capture starts only while resumed and stops promptly on pause, action, and destruction.
- [~] Build and test core, foss, and gplay debug/release variants. Core debug/release and foss debug builds pass; foss release and gplay remain outstanding.
- [~] Run unit tests, instrumentation tests, lint, and detekt. Core debug unit tests and detekt pass; instrumentation and lint remain outstanding.
- [~] Run release shrinking, native-load checks, checksum validation, and installed-size measurement. Core release shrinking and build-time checksums pass; device native loading and installed-size measurement remain outstanding.

### Local prototype verification

Verified in this worktree on 2026-08-05:

- `:voice-qualification:build` passes for the benchmark harness.
- `testCoreDebugUnitTest` passes, including command, session, capability, permission, and endpoint-detector coverage.
- `:app:detekt` passes across the prototype runtime and the existing app sources.
- `assembleCoreDebug` passes and produces a 106,613,894-byte APK.
- `assembleCoreRelease` passes with shrinking and produces an 84,392,011-byte APK.
- `assembleFossDebug` passes and produces a 142,035,994-byte APK containing both arm64 and x86_64 native runtimes.
- `verifyVoiceArtifacts` validates the pinned AAR, model, token, and license SHA-256 values during both app builds.
- The core release APK contains the Moonshine model assets and arm64 sherpa-onnx/ONNX Runtime libraries.

Not yet verified: native loading after the x86_64 emulator packaging fix, physical-device microphone capture and inference, installed size, instrumentation, lint, foss release, or any gplay variant.

## Promotion record

Before promoting the current prototype for release, commit:

- Raw per-device qualification results.
- The selected runtime/model versions and decoding/VAD/endpoint parameters.
- The benchmarked confidence or keyword trigger threshold.
- Runtime and weight licenses permitting redistribution.
- SHA-256 values for every bundled binary and model file.
- Installed-size and native-load results for core, foss, and gplay release variants.
