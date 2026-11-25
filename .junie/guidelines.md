## Operational Guidelines
* Only generate code in this project directory.
* Follow this project's existing conventions and patterns.

## External Dependencies and Utilities
* This project relies on a local shared utility library located at the relative path: `../commons/`
* Before generating any new helper methods, string formatters, or UI component factories, analyze the source files located in `../commons/src/main/java/`.
* Reuse existing utility classes from that directory instead of writing redundant logic.
* Refer to the `../commons/docs/` directory for more insights how to use the classes in the Commons project.

## Architectural and Coding Style Models

### Design Patterns & Design Principles
* Use the Singleton pattern for managing database connections.
* Implement the Repository pattern for encapsulating database access logic.
* Use the Factory pattern for creating database objects.
* Use the Builder pattern for constructing complex objects.
* Use the Observer pattern for notifying observers of changes to the database.
* Do not use positional command line arguments. Use one of the `ArgumentParser` implementations (e.g., `PosixParser`) in the `coyote.commons.cli` package of the `commons` repository to parse command line arguments.

### Code Style and Conventions
* Mimic the naming conventions, logging configurations, and Java 26 idiomatic patterns established in the `../commons/` repository.
* Use Clean Code principles and practices as published by Robert C. Martin.
* Create static constants whenever possible for string literals and other values that are used multiple times throughout the code.
* All methods, regardless of access must have complete documentation. Javadoc comments are to be thorough, complete and updated whenever code in the method changes.

### Task Class Pattern
* A Task Class is a class that contains a main method to create method parameters from the command line and a separate method to perform the business logic using passed parameters.
* The main method is only used to collect method parameters, call the business logic method then render the results.
* Task Classes are used to encapsulate business logic and make it easy to test and reuse.
* Task Classes can be use by both command line applications and JavaFX applications.
* JavaFX components generate parameters from the UI, call the task's logic method, then render the results in the UI.
* When asked to create a "Task Class", create a class that can be used by both command line applications and JavaFX applications.

### Unit Testing
* All generated code must contain full unit testing coverage
* All unit tests must be written in JUnit 6
* Use Mockito 5 in Unit tests when appropriate.

## JavaFX
* The look, feel, and style of all JavaFX components must match that of the existing JavaFX components unless otherwise directed.
* All JavaFX components must appear to be a high-quality desktop application and not look like a web page or mobile app.
