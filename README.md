# BPMN-Prolog
Java application that translates a Camunda BPMN diagram to a prolog rule-based file.

## Usage

Run the .jar file, the program will display a button to select a camunda bpmn file to translate to a prolog file,
the translated rule-based file will be displayed on the "generated prolog files" section in the middle of the screen.

## Considerations

The goal of the software is to generate rule-based logic (in prolog) from bpmn files, so for querying the generated prolog files
it must make use of utilities like SWI Prolog.

