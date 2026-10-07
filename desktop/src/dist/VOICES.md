# The voices that read the stories

MythoStory reads the stories aloud with **Piper**, a neural speech synthesis that runs on this
computer, offline: https://github.com/rhasspy/piper — MIT licence, © Michael Hansen.
Its directory, `piper/`, also holds what Piper needs to run: espeak-ng (GPL-3.0-or-later, which turns
the text into phonemes) and ONNX Runtime (MIT).

The voices, in `voices/`, come from https://huggingface.co/rhasspy/piper-voices (v1.0.0):

| File          | Voice                      | Trained on                                                    | Licence   |
|---------------|----------------------------|---------------------------------------------------------------|-----------|
| `fr.onnx`     | fr_FR-siwis-medium (French)  | the SIWIS French Speech Synthesis Database, University of Edinburgh — https://datashare.is.ed.ac.uk/handle/10283/2353 | CC BY 4.0 |
| `en.onnx`     | en_GB-alba-medium (English)  | the Alba dataset, University of Edinburgh — https://datashare.ed.ac.uk/handle/10283/3270 | CC BY 4.0 |

CC BY 4.0: https://creativecommons.org/licenses/by/4.0/
