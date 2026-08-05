# Voice model qualification harness

This module is not an app dependency and is never packaged. It feeds 16 kHz mono PCM16 WAV files
through the same `VoiceRecognitionEngine` interface used by the alarm.

The tab-separated corpus manifest has six fields: `id`, `category`, `expected`, relative WAV path,
`speaker`, and `condition`. Categories are `normal_command`, `whisper_command`, `normal_wer`,
`whisper_wer`, and `negative`. Native candidate runners receive WAV bytes on stdin and print
`text<TAB>confidence<TAB>inference_milliseconds`. The harness supplies `--candidate` with one of
`moonshine`, `whisper`, or `zipformer`.

Run with:

```sh
./gradlew :voice-qualification:run --args='--candidate moonshine --corpus /corpus/manifest.tsv --runner /runner/sherpa-moonshine --threshold 0.82'
```

The output includes command recall, false actions, WER, p95 inference latency, and raw clip rows.
Readiness, utterance-end-to-action latency, thermal state, and installed size must still be measured
by the Android device runner and recorded in the promotion record.
