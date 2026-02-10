The `jacob-1.18-x64.dll` (Java COM Bridge) is automatically downloaded from
GitHub releases during the Maven build via `maven-antrun-plugin`.

No manual download or placement is needed.

The build downloads https://github.com/freemansoft/jacob-project/releases (tag Root_B-1_18),
extracts the x64 DLL, and places it on the classpath. The ZIP is cached in target/jacob/
so it's only downloaded once per clean build.

At runtime, `OutlookClient` extracts the DLL from the classpath to a temp directory
and sets `jacob.dll.path` so the Jacob library can find it.
