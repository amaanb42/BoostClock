# Offline voice-control third-party notices

## sherpa-onnx 1.13.4

- Project: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0
- Bundled artifact: `app/libs/sherpa-onnx-1.13.4.aar`
- Complete license: `app/src/main/assets/voice/LICENSE.sherpa-onnx.txt`
- SHA-256: `03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`

The artifact digest matches the digest published in the official GitHub v1.13.4 release metadata.

## Moonshine v2 Tiny English quantized (2026-02-27)

- Upstream model: https://github.com/moonshine-ai/moonshine
- sherpa-onnx package: `sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27`
- License: MIT; the complete license is bundled beside the model assets.
- Download archive SHA-256: `9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d`

The archive digest matches the digest published in the official GitHub `asr-models` release metadata.

The build verifies the runtime and each packaged model file before compiling. The application also
verifies every model asset before initializing the native recognizer.
