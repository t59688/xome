# SQLCipher's AAR supplies its JNI keep rules; Room supplies the rules for its
# generated database implementation. Keep using those dependency consumer rules.
#
# Argon2 uses Bouncy Castle's lightweight API directly, not a reflectively loaded
# JCA provider. R8 follows those calls and retains the required crypto code.
# Avoid package-wide keep rules: they retain unused providers, algorithms and UI.
