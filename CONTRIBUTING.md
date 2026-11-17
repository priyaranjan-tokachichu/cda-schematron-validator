# Contributions and repository policy

Issues are welcome for bug reports, feature requests, example documents, and standards updates. Use synthetic or de-identified examples; never attach patient information or credentials.

Pull requests are reserved for the repository owner, `priyaranjan-tokachichu`. External pull requests cannot be disabled independently of issues on this public GitHub repository; the owner and branch policy check prevents them from satisfying the merge gate.

All changes to `main` must go through a pull request, including changes by the owner. No direct pushes, force pushes, or branch deletion are allowed on `main`. Owner-authored PRs do not require another person's approval. Merge authorization remains with the owner.

Use descriptive branch names such as `feature/validation-api`, `fix/document-isolation`, or `docs/quick-start`. Never name branches after development tools, editors, AI assistants, models, or automation providers. See `AGENTS.md`.

Run `npm test` for legacy API and client changes. Run `npm run service:test` for the service, which prepares pinned official validation packages and runs both focused tests and real-package integration tests. `docker compose up --build` runs the complete local UI and API.

When updating standards assets: review the official release, update the immutable URL and SHA-256 in `profiles/sources.lock.json`, review the upstream conversion report and vocabulary changes, regenerate assets, and run all tests plus the benchmark. Do not replace packages silently at runtime. The upstream watch workflow reports new releases without changing files or opening a PR.
