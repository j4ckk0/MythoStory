# The voices that read the stories

MythoStory reads the stories aloud with **Piper**, a neural speech synthesis that runs on this
computer, offline: https://github.com/rhasspy/piper — MIT licence, © Michael Hansen.
Its directory, `piper/`, also holds what Piper needs to run: espeak-ng (GPL-3.0-or-later, which turns
the text into phonemes; its source: https://github.com/espeak-ng/espeak-ng) and ONNX Runtime (MIT).

The voices, in `voices/`, come from https://huggingface.co/rhasspy/piper-voices (v1.0.0); the page's
*Settings* choose among them:

| File                                      | Voices                     | Trained on                                                    | Licence      |
|-------------------------------------------|----------------------------|---------------------------------------------------------------|--------------|
| `fr_FR-siwis-medium.onnx`                 | Siwis (French)             | the SIWIS French Speech Synthesis Database, University of Edinburgh — https://datashare.is.ed.ac.uk/handle/10283/2353 | CC BY 4.0    |
| `fr_FR-upmc-medium.onnx`                  | Jessica, Pierre (French)   | the UPMC corpus of MaryTTS, Université Pierre-et-Marie-Curie — https://github.com/marytts/upmc-pierre-data | CC BY-SA 4.0 |
| `en_GB-alba-medium.onnx`                  | Alba (English)             | the Alba dataset, University of Edinburgh — https://datashare.ed.ac.uk/handle/10283/3270 | CC BY 4.0    |
| `en_GB-cori-medium.onnx`                  | Cori (English)             | LibriVox recordings — https://librivox.org                    | public domain |
| `en_GB-northern_english_male-medium.onnx` | Northern (English)         | Google's crowdsourced UK and Ireland English dialect speech — https://www.openslr.org/83/ | CC BY-SA 4.0 |

CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ —
CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ (the voice models trained on these
datasets are shared under the same licence).
