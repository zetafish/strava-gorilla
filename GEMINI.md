# Project: Strava FIT Analyzer

## Project Overview

This is a data analysis project built with Clojure and Babashka to analyze personal fitness data from Strava. The primary data source is FIT (Flexible and Interoperable Data Transfer) files, which are downloaded from Strava and processed.

The project is structured as a command-line interface (CLI) application powered by Babashka, providing various tasks for data synchronization and reporting. It also includes capabilities for more interactive analysis and development using a REPL and Clerk notebooks.

## Key Files & Directories

*   **`src/`**: Contains the Clojure source code, organized into namespaces based on functionality (e.g., `strava.api`, `strava.parser`, `strava.report`).
*   **`.data/`**: The default directory for storing downloaded Strava data, including activity details and original FIT files.
*   **`analysis/`**: Holds markdown files containing specific, in-depth analyses of activities or training periods.
*   **`deps.edn`**: Defines the project's dependencies for the JVM-based Clojure environment, including libraries for data processing, web interaction, and development tools.
*   **`bb.edn`**: Babashka configuration file that defines the CLI tasks, their dependencies, and entry points. This is the main definition for the project's command-line functionality.
*   **`smoke.sh`**: A shell script that runs a series of reporting commands to verify that the core functionality is working correctly.

## Setup

1.  **Install Dependencies**: Ensure you have [Babashka](https://github.com/babashka/babashka) and the [Clojure CLI](https://clojure.org/guides/install_clojure) installed.
2.  **Strava API**: Create a Strava API application at `https://www.strava.com/settings/api`.
3.  **Authentication**: Create a `.auth.edn` file in the project root with your Strava API credentials and session cookie:
    ```edn
    {:client-id 12345
     :client-secret "your-secret"
     :session-cookie "your-strava4-session-cookie"}
    ```
4.  **Authorization**: Authorize the application by visiting the authorization URL specified in `README.md` and using the `code` parameter to get an access token.

## Building and Running

This project is primarily run as a series of Babashka tasks.

### Main Commands

*   **Sync Data**: Download activities for a specific period.
    ```bash
    # Sync for a specific month
    bb sync -y 2026 -m 8
    ```

*   **Generate Reports**: Run various reports on the downloaded data.
    ```bash
    # Report on splits for a specific activity
    bb report:splits --id <activity-id> --by-even 2

    # Report on activities within a date range
    bb report:activities --from YYYY-MM-DD --to YYYY-MM-DD

    # Generate weekly or monthly aggregate reports
    bb report:weeks --from YYYY-MM-DD --to YYYY-MM-DD
    bb report:months --from YYYY-MM-DD --to YYYY-MM-DD
    ```

### Running Tests

Execute the smoke test script to ensure the main reporting functions are working.
```bash
./smoke.sh
```

## Development Conventions

*   **CLI Tasks**: Core functionality is exposed as Babashka tasks defined in `bb.edn`. When adding new CLI commands, follow the existing structure.
*   **Interactive Development**: For more complex development and data exploration, the project is set up to be used with a Clojure REPL.
    ```bash
    clj -X:repl
    ```
*   **Notebooks**: The project uses `nextjournal/clerk` for literate programming and data visualization. You can start the Clerk server to view and interact with the notebooks.
    ```bash
    clj -X:clerk
    ```
