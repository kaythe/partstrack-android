# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- An app with partstrack's logo as its icon, in the device's colours.
- Setup on first start, and settings later: the server's address and an
  optional API token, with a check that says whether the server can be
  reached, is partstrack, is new enough, and accepts the token. Without a
  token the app only reads. The token is stored encrypted with a key that
  never leaves the phone, and the app's data is left out of backups.
- Plain HTTP works, as partstrack-web serves it; the settings warn that the
  token then travels unencrypted.
- A light, dark or system theme.
- The parts list: search as you type (including attribute filters such as
  `resistance>=10k`), filters for low stock, location (with its
  sublocations), tag and category, and pull to refresh. Low stock shows in
  red. The list reloads when the app comes back into view, and says what is
  wrong when the server can't be reached.
