# Bouncy Castle is used only through its lightweight API (Argon2, HKDF); nothing is loaded
# reflectively, so R8 can shrink it without keep rules.
-dontwarn org.bouncycastle.**
