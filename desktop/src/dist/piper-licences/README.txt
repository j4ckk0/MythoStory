The licences of what piper/ holds

MythoStory bundles Piper as its authors publish it (https://github.com/rhasspy/piper, release 2023.11.14-2), unmodified,
and runs it as a separate program. The archive Piper publishes carries no licence text, so the texts are here.
Each comes from its component's own repository at the version that Piper release was built from, but two:
ONNX Runtime's are those its 1.14.1 release ships (its Python package on PyPI, as its repository could not be
reached), and libtashkeel's is its repository's current one, as the model has no version of its own.

  Piper 2023.11.14-2                    MIT              piper-LICENSE.md
    with {fmt} 10.0.0 and spdlog 1.12.0 MIT              fmt-LICENSE.rst, spdlog-LICENSE
  piper-phonemize 2023.11.14-4 (1.2.0)  MIT              piper-phonemize-LICENSE.md
    with libtashkeel's model            MIT              libtashkeel-LICENSE   (libtashkeel_model.ort)
  eSpeak NG 1.52, Piper's fork          GPL-3.0-or-later espeak-ng-COPYING     (espeak-ng, libespeak-ng,
                                                                                espeak-ng.dll, espeak-ng-data/)
    and the parts its sources license otherwise:          espeak-ng-COPYING.BSD2 (getopt, on Windows),
                                                          espeak-ng-COPYING.UCD (the Unicode data),
                                                          espeak-ng-COPYING.APACHE
  ONNX Runtime 1.14.1                   MIT              onnxruntime-LICENSE, onnxruntime-ThirdPartyNotices.txt
                                                         (libonnxruntime, onnxruntime.dll)

eSpeak NG's source code
-----------------------
eSpeak NG is free software under the GNU General Public License, version 3 or later (espeak-ng-COPYING). The
complete source code of the version piper/ holds is Piper's fork of eSpeak NG at commit
0f65aa301e0d6bae5e172cc74197d32a6182200f:

  https://github.com/rhasspy/espeak-ng/tree/0f65aa301e0d6bae5e172cc74197d32a6182200f
  https://github.com/rhasspy/espeak-ng/archive/0f65aa301e0d6bae5e172cc74197d32a6182200f.zip

and the build that links it, piper-phonemize 2023.11.14-4:

  https://github.com/rhasspy/piper-phonemize/tree/2023.11.14-4

The voices in voices/ have their own licences: VOICES.md, beside piper/.
