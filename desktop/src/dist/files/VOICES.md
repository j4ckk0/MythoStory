# The voices that read the stories

MythoStory reads the stories aloud with **Piper** 2023.11.14-2, a neural speech synthesis that runs on this
computer, offline: https://github.com/rhasspy/piper — MIT licence, © Michael Hansen.
Its directory, `piper/`, also holds what Piper needs to run: piper-phonemize 1.2.0 (MIT), eSpeak NG 1.52
(GPL-3.0-or-later, which turns the text into phonemes; its source: https://github.com/espeak-ng/espeak-ng) and
ONNX Runtime 1.14.1 (MIT). Their licence texts, and where eSpeak NG's exact source code is, are in `piper/licences/`.

The voices, in `voices/`, come from https://huggingface.co/rhasspy/piper-voices (v1.0.0); the page's
*Settings* choose among them:

| File                         | Voices                   | Trained on                                                                                     | Licence       |
|------------------------------|--------------------------|------------------------------------------------------------------------------------------------|---------------|
| `fr_FR-mls-medium.onnx`      | Claire, Antoine (French) | the French part of Multilingual LibriSpeech (MLS), read from LibriVox audiobooks — https://www.openslr.org/94/ (readers 3060 and 4699 of its 125) | CC BY 4.0     |
| `en_GB-cori-medium.onnx`     | Cori (English)           | LibriVox recordings — https://librivox.org                                                     | public domain |
| `en_US-kristin-medium.onnx`  | Kristin (English)        | LibriVox recordings — https://librivox.org                                                     | public domain |
| `en_US-ljspeech-medium.onnx` | LJ (English)             | the LJ Speech dataset, LibriVox recordings — https://keithito.com/LJ-Speech-Dataset/           | public domain |
| `en_US-norman-medium.onnx`   | Norman (English)         | LibriVox recordings — https://librivox.org                                                     | public domain |

CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ — MLS: Pratap, Xu, Sriram, Synnaeve and Collobert, "MLS: A
Large-Scale Multilingual Dataset for Speech Research", Interspeech 2020, https://arxiv.org/abs/2012.03411.

Every voice was trained from scratch on its own recordings, none fine-tuned from another voice, as each model card
says (https://huggingface.co/rhasspy/piper-voices). Cori, Kristin, LJ and Norman were trained by Bryce Beattie:
https://brycebeattie.com/files/tts/.
