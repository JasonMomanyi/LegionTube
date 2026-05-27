# Contributing to LegionTube

Thank you for your interest in contributing to LegionTube! We welcome contributions from the community to make this privacy-focused YouTube client even better.

## 🐛 Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear description** of the issue
- **Steps to reproduce** the behavior
- **Expected behavior** vs actual behavior
- **Screenshots** if applicable
- **Device information** (Android version, device model)
- **App version** or commit hash

## 💡 Suggesting Features

Feature suggestions are welcome! Please:

- Check if the feature has already been suggested
- Provide a clear description of the feature
- Explain why this feature would be useful
- Include mockups or examples if possible

## 🔧 Pull Requests

### Before You Start

1. Fork the repository
2. Create a new branch from `main`
3. Make sure you can build the project using `./gradlew assembleDebug`

### Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/LegionTube.git
cd LegionTube

# Add upstream remote
git remote add upstream https://github.com/JasonMomanyi/LegionTube.git

# Create a feature branch
git checkout -b feature/your-feature-name
```

### Code Guidelines

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused
- Write self-documenting code

### Commit Messages

Use clear, descriptive commit messages:

```
✨ Add new theme selector
🐛 Fix music player crash on rotation
📝 Update README with new features
♻️ Refactor video player logic
🎨 Improve UI spacing in settings
```

**Commit prefixes:**
- ✨ `:sparkles:` - New feature
- 🐛 `:bug:` - Bug fix
- 📝 `:memo:` - Documentation
- ♻️ `:recycle:` - Refactoring
- 🎨 `:art:` - UI/styling
- ⚡ `:zap:` - Performance
- 🧪 `:test_tube:` - Tests
- 🔧 `:wrench:` - Configuration

### Testing

- Test your changes thoroughly
- Ensure the app builds without errors
- Test on different screen sizes if possible
- Check for memory leaks

### Submitting Your PR

1. **Update your fork:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Push your changes:**
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create Pull Request:**
   - Go to GitHub and create a PR
   - Fill out the PR template
   - Link any related issues
   - Wait for review

## 📋 Code Review Process

- Maintainers will review your PR
- Address any feedback or requested changes
- Once approved, your PR will be merged
- Your contribution will be credited in releases

## 🎯 Areas We Need Help

- [ ] Improving documentation
- [ ] Writing unit tests
- [ ] UI/UX improvements
- [ ] Performance optimization
- [ ] Bug fixes
- [ ] Accessibility features
- [ ] Translations

## 📜 Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Give constructive feedback
- Focus on the code, not the person
- Help create a positive community

## ❓ Questions?

If you have questions, feel free to:

- Open a discussion on GitHub
- Comment on existing issues
- Reach out to Lord Stunnis

## 🙏 Thank You!

Every contribution helps make LegionTube better. Thank you for being part of the community!

---

**Happy Coding! 🚀**
