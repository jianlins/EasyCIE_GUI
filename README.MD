## What is EasyCIE
EasyCIE(Easy Clinical Information Extractor) is a rule-based clinical information extraction tool designed for non-NLP(natural language processing) expert users. It a GUI wrapper on top of EasyCIE, an UIMA-based command line version that allows executing on servers.

## What's new in 3.0.0
1. Add Adaptable CPE descriptor runner, which allows run cpe descriptor with customized rule-based AE component with dynamically generated types, and allows update component configurations after compilation.
2. Add Adaptable AEs runner, which read AE descriptors from a directory, uses the project configuration xml to update AE configurations, dynamically generate rule-base types.


### TODO
1. Add a menu to allow import a CPE descriptor and set up a default element in the project configuraiton xml.
1. Add a menu to allow import an AE descriptor directory and set up a default element in the project configuraiton xml.
 