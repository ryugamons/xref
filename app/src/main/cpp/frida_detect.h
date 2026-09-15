#pragma once

/**
 * Checks for several Frida-related signals (port, task names, memory maps).
 * Returns true if instrumentation is detected.
 */
bool detectFridaInstrumentation();
