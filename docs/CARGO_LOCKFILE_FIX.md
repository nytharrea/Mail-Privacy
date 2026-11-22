# Cargo.lock workflow fix

The repository archive did not contain a root `Cargo.lock`. The previous workflow
used `git diff --quiet -- Cargo.lock`, which ignores untracked files, so the first
generated lockfile could be mistaken for an unchanged tracked file.

The commit step now checks `git status --porcelain -- Cargo.lock`. This detects
tracked modifications and newly created untracked files. The workflow can then
`git add`, commit, and push the first generated lockfile.

## Required action

Run **Update Cargo.lock** from GitHub Actions once after merging this workflow
change. The workflow generates the workspace lockfile, validates it with
`cargo metadata --locked`, runs `cargo check --locked --workspace --all-targets
--all-features`, and commits the file.

This archive intentionally does not fabricate a Cargo.lock: Cargo was not
available in the editing environment. Keep the generated root `Cargo.lock` in
the repository and include it in future source archives.
