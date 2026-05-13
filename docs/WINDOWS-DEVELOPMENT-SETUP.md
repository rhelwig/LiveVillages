# Windows Setup For Live Villages And Other Java Mods

This guide is for a first-time Windows development machine. It is written for someone who has never built a Java mod before.

The goal is to end with:

- a GitHub account
- Git installed
- Java `25` installed
- VS Code installed and set up for Java
- a `Projects` folder inside your home folder
- this repository cloned into `Projects/LiveVillages`
- a successful build
- a working development Minecraft client

This same general setup also works for many other Java mods that use Gradle. The main thing that changes from project to project is the Java version.

## What You Will Install

- GitHub account: <https://github.com/signup>
- Git for Windows: <https://git-scm.com/download/win>
- Eclipse Temurin JDK `25`: <https://adoptium.net/temurin/releases/?version=25>
- VS Code: <https://code.visualstudio.com/docs/setup/windows>
- VS Code `Extension Pack for Java`: <https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack>
- VS Code `Gradle for Java`: <https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle>

## Before You Start

- Make sure Windows Update has finished and your laptop is fully restarted.
- Make sure you can sign in as the normal user on the laptop.
- Make sure you have a stable internet connection. The first build downloads a lot of Java, Minecraft, Fabric, and Gradle files.

## 1. Create A GitHub Account

1. Open <https://github.com/signup>.
2. Create an account.
3. Verify your email address if GitHub asks you to.
4. Save your GitHub username and password in a password manager or somewhere safe.

You do not need to pay for GitHub for this project.

## 2. Install Git For Windows

1. Open <https://git-scm.com/download/win>.
2. Download and run the installer.
3. If you are not sure which options to choose, the default choices are fine.
4. Finish the install.

Git is what lets you clone repositories, pull updates, and later push your own changes.

## 3. Install Java 25

This repository currently requires Java `25`.

1. Open <https://adoptium.net/temurin/releases/?version=25>.
2. Download the Windows x64 JDK installer for Temurin `25`.
3. Run the installer.
4. If the installer offers normal setup options, the defaults are fine.
5. Finish the install.

After installation:

1. Open the Start menu.
2. Search for `PowerShell`.
3. Open `Windows PowerShell`.
4. Run:

```powershell
java -version
```

You should see Java `25` in the output.

If that command says Java is not recognized, restart the laptop and try again.

## 4. Install VS Code

1. Open <https://code.visualstudio.com/docs/setup/windows>.
2. Download the Windows installer.
3. Run the installer.
4. If you are asked to choose between `User Setup` and `System Setup`, choose `User Setup`.
5. Finish the install.

Why `User Setup`:

- it usually does not need administrator access
- it is the preferred Windows setup for VS Code
- it updates more smoothly for a normal single-user laptop

## 5. Set Up VS Code For Java

1. Open VS Code.
2. Click the Extensions icon on the left side.
3. Search for `Extension Pack for Java`.
4. Install it.
5. Search for `Gradle for Java`.
6. Install it too.
7. Close and reopen VS Code after both extensions finish installing.

Why these extensions:

- `Extension Pack for Java` gives you Java editing, debugging, testing, and project support
- `Gradle for Java` helps VS Code understand Gradle-based Java projects like this one

## 6. Create A `Projects` Folder In Your Home Folder

Your home folder on Windows is usually:

```text
C:\Users\YourUserName
```

The easiest way to make the folder is with PowerShell.

1. Open `Windows PowerShell`.
2. Run:

```powershell
cd $HOME
mkdir Projects -Force
cd .\Projects
```

If the folder already exists, that is fine.

After this step, you should have:

```text
C:\Users\YourUserName\Projects
```

## 7. Optional But Recommended: Tell Git Who You Are

If you ever make commits, Git should know your name and email first.

In PowerShell, run:

```powershell
git config --global user.name "Your Name"
git config --global user.email "your-email@example.com"
```

Use the same email address you used for GitHub if you want GitHub to connect your commits to your account.

You only need to do this once per computer.

## 8. Clone The Repository

In PowerShell:

```powershell
cd $HOME\Projects
git clone https://github.com/rhelwig/LiveVillages.git
cd .\LiveVillages
```

After cloning, the project should live here:

```text
C:\Users\YourUserName\Projects\LiveVillages
```

## 9. Open The Project In VS Code

From the same PowerShell window, run:

```powershell
code .
```

If that works, VS Code will open the `LiveVillages` folder.

If `code` is not recognized:

1. Open VS Code normally from the Start menu.
2. Choose `File` -> `Open Folder...`
3. Open:

```text
C:\Users\YourUserName\Projects\LiveVillages
```

The first time VS Code opens the project, give it a minute. Java and Gradle extensions may still be starting.

## 10. Build The Mod

In VS Code:

1. Open the terminal with `Terminal` -> `New Terminal`.
2. Make sure the terminal says it is inside the `LiveVillages` folder.
3. Run:

```powershell
.\gradlew.bat build
```

Important:

- the first build can take a while
- Gradle will download dependencies
- Minecraft and Fabric files will also be downloaded

If the build succeeds, the mod jar will be created as part of the Gradle build.

## 11. Run The Development Client

Still in the VS Code terminal, run:

```powershell
.\gradlew.bat runClient
```

This launches a development Minecraft client with the mod loaded.

The first launch may take several minutes. That is normal.

## 12. Day-To-Day Commands

When working on the mod later, these are the commands you will use most:

Build:

```powershell
.\gradlew.bat build
```

Run the dev client:

```powershell
.\gradlew.bat runClient
```

If you are new to Git and GitHub workflow, also read:

- [GIT-GITHUB-FOR-NEWBS.md](GIT-GITHUB-FOR-NEWBS.md)
- [CONTRIBUTOR-GLOSSARY.md](CONTRIBUTOR-GLOSSARY.md)

## 13. Troubleshooting

### `java` is not recognized

Try these in order:

1. Close PowerShell and reopen it.
2. Restart the laptop.
3. Run `java -version` again.
4. If it still fails, reinstall Temurin `25`.

### `git` is not recognized

1. Close PowerShell and reopen it.
2. Run `git --version`.
3. If it still fails, reinstall Git for Windows.

### VS Code does not understand the Java project

1. Make sure `Extension Pack for Java` is installed.
2. Make sure `Gradle for Java` is installed.
3. Close and reopen VS Code.
4. Wait a minute for Java import to finish.

### The first Gradle build fails because of downloads

That usually means a temporary network problem.

Try the same command again:

```powershell
.\gradlew.bat build
```

### `code .` does not work

Open VS Code from the Start menu and use `File` -> `Open Folder...` instead.

### Minecraft does not appear right away after `runClient`

Wait a few minutes on the first run. The first startup is much slower than later ones.

## 14. For Other Java Mods

This setup is a good default for many Java mod projects:

- Git for source control
- Temurin JDK for Java
- VS Code for editing
- Gradle wrapper commands for building and running

Before building a different mod, always check:

- which Java version it needs
- whether it uses `gradlew.bat`
- whether it has project-specific setup notes in its `README.md`
