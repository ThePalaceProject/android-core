org.thepalaceproject.palace.diagnostics.api
===

The `org.thepalaceproject.palace.diagnostics.api` module provides a small, explicit API for
reporting *notable anomalies* to a diagnostics backend.

This is a low-volume, high-signal channel for specific, mostly-silent failures that do not
otherwise produce a crash or a user-visible error, but for which fleet-wide visibility is
wanted without requesting on-device logs. It is not a replacement for normal logging:
ordinary `Logger` output continues to go to the file and logcat appenders only.
