The `jacob-1.17-x64.dll` (Java COM Bridge) is automatically copied from the
internal Maven repository during the build via `maven-dependency-plugin`.

No manual download or placement is needed.

The build resolves `net.sf.jacob-project:jacob:1.17:x64:dll` from the Maven repo
and copies it to this directory on the classpath. At runtime, `OutlookClient`
extracts the DLL to a temp directory and sets `jacob.dll.path`.

If the classifier in your internal repo differs from `x64`, update the
`<classifier>` value in the maven-dependency-plugin config in pom.xml.
