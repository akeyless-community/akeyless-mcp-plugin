# Publishing to JetBrains Marketplace

This guide will help you publish the Akeyless MCP plugin to the official JetBrains Marketplace.

## Prerequisites

1. **JetBrains Account**: Create an account at https://account.jetbrains.com/
2. **Plugin Developer Account**: Request plugin developer access at https://plugins.jetbrains.com/author/me
3. **Plugin Signing Certificate**: You'll need a code signing certificate (optional but recommended)

## Step 1: Prepare Plugin Metadata

### Update plugin.xml

Ensure your `plugin.xml` has complete metadata:

- ✅ Plugin ID: `com.akeyless.mcp` (already set)
- ✅ Name: "Akeyless MCP" (already set)
- ✅ Version: Update for each release
- ✅ Vendor information: Already set
- ✅ Description: Should be comprehensive (see below)
- ✅ Change notes: Update for each release

### Enhanced Description Template

Update the description in `plugin.xml` with more details:

```xml
<description><![CDATA[
<h2>Akeyless MCP Plugin for JetBrains IDEs</h2>

<p>Integrate Akeyless secrets management directly into your JetBrains IDE through the Model Context Protocol (MCP).</p>

<h3>Features</h3>
<ul>
    <li><b>Browse Secrets:</b> View all your Akeyless items in a tree structure</li>
    <li><b>Query Interface:</b> Natural language queries to interact with Akeyless</li>
    <li><b>MCP Tools:</b> Access all 17+ Akeyless MCP tools directly from the IDE</li>
    <li><b>Create & Manage:</b> Create, update, and delete secrets seamlessly</li>
    <li><b>Secure Authentication:</b> Supports SAML and other Akeyless auth methods</li>
    <li><b>Real-time Updates:</b> Refresh your secrets list anytime</li>
</ul>

<h3>Getting Started</h3>
<ol>
    <li>Install the Akeyless CLI: <code>brew install akeyless/tap/akeyless</code> (macOS) or download from akeyless.io</li>
    <li>Configure your Akeyless profile: <code>akeyless configure --profile saml</code></li>
    <li>Open Settings → Tools → Akeyless MCP</li>
    <li>Configure the server command and arguments</li>
    <li>Open View → Tool Windows → Akeyless MCP</li>
</ol>

<h3>Requirements</h3>
<ul>
    <li>JetBrains IDE 2023.2 or later</li>
    <li>Akeyless CLI installed and configured</li>
    <li>Valid Akeyless account</li>
</ul>

<p>For more information, visit <a href="https://www.akeyless.io">akeyless.io</a></p>
]]></description>
```

## Step 2: Prepare Plugin Icon

Create a plugin icon:
- Size: 40x40 pixels (required)
- Format: PNG with transparency
- Location: `src/main/resources/META-INF/pluginIcon.svg` or `pluginIcon.png`

You can also add:
- `pluginIcon@2x.png` (80x80) for high-DPI displays

## Step 3: Release checklist (each new Marketplace version)

1. **Version** — Set the same value in:
   - `version` in `build.gradle.kts`
   - `<version>` and `<change-notes>` in `src/main/resources/META-INF/plugin.xml`
2. **Compatibility** — `patchPluginXml { sinceBuild.set("232") }` is the minimum IDE (2023.2). There is **no** `until-build` (all newer branches). Raise `sinceBuild` only if you drop support for older IDEs.
3. **JDK for Gradle** — Compilation uses **JDK 17** via Gradle toolchains. If `./gradlew` fails on startup with a very new JDK (e.g. 25), set `org.gradle.java.home` in `gradle.properties` to JDK 17 or 21 (see comment in that file).
4. **Secrets** — Copy `publish.env.example` to `publish.env` (gitignored), add `PUBLISH_TOKEN` and optional signing variables. Never commit tokens.

Your `build.gradle.kts` already wires `signPlugin` and `publishPlugin` from environment variables.

## Step 4: Build and Test

```bash
# Clean, test, and produce the upload ZIP
./gradlew clean test buildPlugin

# Optional: run a sandbox PyCharm with the plugin
./gradlew runIde

# Artifact to upload or publish
ls -lh build/distributions/
```

## Step 5: Create Marketplace Listing

1. **Go to**: https://plugins.jetbrains.com/author/me
2. **Click**: "Add new plugin"
3. **Fill in**:
   - **Plugin name**: Akeyless MCP
   - **Plugin ID**: com.akeyless.mcp (must match plugin.xml)
   - **Category**: Tools (or Security)
   - **Tags**: secrets, security, akeyless, mcp, devops
   - **Repository URL**: Your GitHub repo (if public)
   - **Support URL**: https://www.akeyless.io/support (or your support page)

## Step 6: Upload Plugin

### Option A: Manual Upload

1. Build the plugin: `./gradlew buildPlugin`
2. Go to your plugin page on marketplace
3. Click "Upload new version"
4. Upload the ZIP file from `build/distributions/`
5. Fill in release notes
6. Submit for review

### Option B: Automated Publishing (Recommended)

1. **Get your publish token**: https://plugins.jetbrains.com/author/me/token  
2. **Configure env** (see `publish.env.example`):
   ```bash
   cp publish.env.example publish.env
   # edit publish.env — set PUBLISH_TOKEN=perm:… and optional signing paths
   set -a && source publish.env && set +a
   ```
3. **Build, sign (if env set), and publish**:
   ```bash
   ./gradlew clean test buildPlugin signPlugin publishPlugin
   ```
   If you are not using signing yet, omit `signPlugin` or leave signing variables unset (depending on your Marketplace requirements).

## Step 7: Plugin Review Process

After submission:
1. **Initial Review**: JetBrains team reviews (usually 1-3 business days)
2. **Feedback**: They may request changes or clarifications
3. **Approval**: Once approved, your plugin goes live!

### Common Review Feedback

- **Missing documentation**: Ensure README is comprehensive
- **Security concerns**: Make sure you're not storing credentials insecurely
- **Compatibility issues**: Test on multiple IDE versions
- **Missing icons**: Add proper plugin icons

## Step 8: Next release

Repeat the [release checklist](#step-3-release-checklist-each-new-marketplace-version), then `./gradlew clean test buildPlugin` and upload or `publishPlugin` again. The Marketplace rejects a version number that was already uploaded, so always increment the plugin version.

## Best Practices

1. **Versioning**: Use semantic versioning (MAJOR.MINOR.PATCH)
2. **Changelog**: Always update change notes for each release
3. **Testing**: Test on multiple IDE versions before publishing
4. **Documentation**: Keep README updated
5. **Support**: Respond to user reviews and issues promptly
6. **Security**: Never commit tokens or certificates to git

## Troubleshooting

### Build Issues
- Ensure Java 17+ is installed
- Check Gradle wrapper version compatibility
- Verify all dependencies are available

### Publishing Issues
- Verify `PUBLISH_TOKEN` is set correctly
- Check plugin ID matches between plugin.xml and marketplace
- Ensure version number is incremented

### Review Rejection
- Read feedback carefully
- Address all concerns
- Resubmit with improvements

## Resources

- **Marketplace Guidelines**: https://plugins.jetbrains.com/docs/marketplace/marketplace-plugin-requirements.html
- **Plugin Development**: https://plugins.jetbrains.com/docs/intellij/getting-started.html
- **Publishing Guide**: https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html
- **Support Forum**: https://intellij-support.jetbrains.com/hc/en-us/community/topics/200366979-IntelliJ-IDEA-Open-API-and-Plugin-Development

## Checklist Before Publishing

- [ ] Plugin builds successfully
- [ ] Plugin tested on target IDE versions
- [ ] README.md is complete and accurate
- [ ] Plugin icon is added (40x40px minimum)
- [ ] Description in plugin.xml is comprehensive
- [ ] Change notes are filled in
- [ ] Vendor information is correct
- [ ] Version number is set correctly
- [ ] Compatibility range is appropriate
- [ ] No hardcoded credentials or tokens
- [ ] Error handling is robust
- [ ] Logging doesn't expose sensitive data

Good luck with your publication! 🚀
