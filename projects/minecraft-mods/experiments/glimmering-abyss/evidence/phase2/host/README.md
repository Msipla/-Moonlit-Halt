# Actual cloud-host procedure archive

These are the authored scripts used for this run, archived with their original cloud-specific absolute paths. They are not mod runtime code and are not portable launcher presets. Do not install the cloud OpenGL/JDK workarounds on a normal desktop.

The portable entry is `tools/run_experiment.py --accept-eula --capture --phase2`; it downloads ordinary official assets and runs the existing Gradle project.

Here the host used a private verified JDK 17/Gradle 8.2.1 cache, the existing `tools/cloud_loom_compat.py` restricted AF_UNIX fix, Ubuntu Xvfb packages, a verified LWJGL GLFW library, and a localhost-only test server. Xvfb, server and client must be children of the same execution namespace. Completed Loom PID lock files were removed only between finished Gradle runs; concurrent builds were never used after the initial canceled stall.

The Java installer downloader timed out. `setup_runtime.py` downloads the identical official libraries with SHA-1 and ZIP checks and calls the unmodified official installer's own launch-JAR writer. No official assets or dependency binaries are distributed in this project. Graphics are in Minecraft's JAR; only two official font assets were prepared, using `--skip-asset-download`. Audio is therefore unverified.

Some raw logs contain loopback proxy ports and cloud paths. These describe this isolated host, not user settings.
