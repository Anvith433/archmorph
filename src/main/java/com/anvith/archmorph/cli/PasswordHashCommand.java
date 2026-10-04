package com.anvith.archmorph.cli;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** {@code archmorph hash-password}: runs without a Spring context or workspace, so it works on a read-only system. */
public final class PasswordHashCommand {

    private PasswordHashCommand() {
    }

    public static int run(String[] args) {
        return run(System.in, System.out);
    }

    /**
     * Prints a bcrypt hash for {@code archmorph.security.auth.users[n].password-hash}. The password is read without
     * echo from the console, or as the first line of standard input (never from the command line, which would leave
     * it in the shell history), and is never printed.
     */
    static int run(InputStream in, PrintStream out) {
        char[] password;
        Console console = in == System.in ? System.console() : null;
        if (console != null) {
            password = console.readPassword("Password: ");
            char[] again = console.readPassword("Repeat: ");
            if (password == null || again == null || !Arrays.equals(password, again)) {
                out.println("The passwords do not match.");
                return 2;
            }
            Arrays.fill(again, ' ');
        } else {
            try {
                String line = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
                password = line == null ? new char[0] : line.toCharArray();
            } catch (IOException e) {
                out.println("The password could not be read.");
                return 1;
            }
        }
        if (password == null || password.length < 12) {
            out.println("Use a password of at least 12 characters.");
            return 2;
        }
        String hash = new BCryptPasswordEncoder(12).encode(CharBuffer.wrap(password));
        Arrays.fill(password, ' ');
        out.println("{bcrypt}" + hash);
        return 0;
    }
}
