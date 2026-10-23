# STCC and Petalix build service

This directory contains the shared panel build runner, managed app patches, and tests. It prepares and verifies both branded APKs from a selected upstream commit. Release signing runs separately from upstream source.

Signing keys and private app defaults stay in the panel’s hosted secrets. No credentials belong in this repository. Publishing a prepared release does not automatically deploy it to customers.
