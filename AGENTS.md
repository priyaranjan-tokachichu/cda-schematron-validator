# Repository working rules

- Branch names must describe the work, using prefixes such as `feature/`, `fix/`, `docs/`, `test/`, or `chore/`.
- Never include the name of an AI assistant, model, editor, automation platform, or other development tool in a branch name. This overrides tool-provided default branch prefixes.
- Never push commits directly to `main` or merge changes into `main` locally. Submit changes through a pull request.
- Only the repository owner, `priyaranjan-tokachichu`, may authorize and perform pull request merges. Do not merge a pull request without the owner's explicit instruction for that pull request.
- Keep public issue reporting available. Do not change repository visibility or restrict issue participation to enforce contribution preferences.
- Avoid common host ports such as 8080 for local services. The validator defaults to 17643, with a configurable Compose host port through `CDA_VALIDATOR_PORT`; keep client defaults and documentation consistent.
