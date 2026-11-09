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
- A part's page: description, stock in total and per location, minimum,
  manufacturer, MPN, category, package, tags, attributes, notes and the
  latest ten stock changes. Pictures show as thumbnails and full size (with
  pinch to zoom); datasheets and other files stored on the server open in
  the app that handles them, links in the browser. The menu opens the part
  in the web interface.
- Stock changes on a part's page, with a token: − and + next to each
  location take or add one at once (taps in a row show right away and are
  sent in order); its menu takes or adds an amount, sets a counted amount
  or moves some or all of it to another location, each with an optional
  note. "Add stock" puts the part in a location. Locations are chosen from
  the existing ones or typed as a path, and new ones are created. The
  dialogs check the input before sending, keep it if the server refuses,
  and offer the settings when the token isn't accepted.
- Adding, changing and deleting parts, with a token. "+" on the parts list
  opens a form for a new part: name, description, manufacturer, MPN,
  category and tags (with the server's as suggestions), package, minimum
  quantity, attributes, notes, and where it is stored with how many. The
  pencil on a part's page opens the same form for that part, which sends
  only what changed, so changes others made meanwhile to other fields stay.
  The form checks the input before sending and keeps it when the server
  refuses, and asks before throwing input away. "Delete…" in the part's
  menu deletes it after asking.
