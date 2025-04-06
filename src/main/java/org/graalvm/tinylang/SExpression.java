package org.graalvm.tinylang;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Basic parser for SExpressions. Basic SExpression example:
 * <pre>
 * ; (1 + 2) + 3
 * (add (add 1 2) 3)
 * </pre>
 * 
 * Use {@link #walk(String, Visitor)} to traverse any SExpression string.
 */
public final class SExpression {

    public static void walk(String source, SExpression.Visitor callback) {
        Token prev = null;
        Token current;
        ArrayDeque<Token> operations = new ArrayDeque<>();
        while ((current = nextToken(source, prev)) != null) {
            if (prev != null && prev.kind == Kind.OPEN) {
                if (current.kind != Kind.IDENTIFIER) {
                    throw new RuntimeException("Expected identifier at but got " + current.kind);
                }
                AtomicReference<Token> nextToken = new AtomicReference<>(current);
                callback.onOpen(current.text(source), () -> {
                    Token p = nextToken.get();
                    Token c = nextToken(source, p);
                    if (c == null || c.kind != Kind.IDENTIFIER) {
                        return null;
                    }
                    nextToken.set(c);
                    return c.text(source);
                });
                operations.push(current);
                current = nextToken.get();
            } else if (current.kind == Kind.CLOSE) {
                if (operations.isEmpty()) {
                    throw new RuntimeException("Unexpected ')' at");
                }
                Token t = operations.pop();
                callback.onClose(t.text(source), t.start - 1, current.end - t.start + 1);
            } else {
                switch (current.kind) {
                case IDENTIFIER:
                    callback.onIdentifier(current.text(source));
                    break;
                case INTEGER:
                    try {
                        callback.onInteger(Integer.parseInt(current.text(source)));
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Invalid number '" + current.text(source) + ".");
                    }
                    break;
                case DOUBLE:
                    callback.onDouble(Double.parseDouble(current.text(source)));
                    break;
                case STRING:
                    callback.onString(source.substring(current.start + 1, current.end - 1));
                    break;
                case OPEN:
                    // just skip forward
                    break;
                case CLOSE:
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            prev = current;
        }
        if (prev != null && prev.kind == Kind.OPEN) {
            throw new RuntimeException("Invalid trailing '('");
        }
    }

    private static Token nextToken(String source, Token prev) {
        int length = source.length();
        int charIndex = prev != null ? prev.end : 0;

        while (charIndex < length) {
            char c = source.charAt(charIndex);
            if (Character.isWhitespace(c)) {
                charIndex++;
                continue;
            } else if (c == '(') {
                return new Token(Kind.OPEN, charIndex, charIndex + 1);
            } else if (c == ')') {
                return new Token(Kind.CLOSE, charIndex, charIndex + 1);
            }

            if (c == ';') {
                while (charIndex < length) {
                    char cc = source.charAt(charIndex);
                    if (cc == '\n') {
                        break;
                    }
                    charIndex++;
                }
                charIndex++;
                continue;
            }

            int startPos = charIndex;
            char cc = (char) -1; // invalid char
            boolean hasDot = false;
            while (charIndex + 1 < length) {
                cc = source.charAt(charIndex + 1);
                if (Character.isWhitespace(cc) || cc == '(' || cc == ')' || cc == ';') {
                    break;
                }
                if (cc == '.') {
                    hasDot = true;
                }
                charIndex++;
            }
            Kind kind;
            if (c == '\"' && cc == '\"') {
                // string
                kind = Kind.STRING;
            } else if (Character.isDigit(c)) {
                if (hasDot) {
                    kind = Kind.DOUBLE;
                } else {
                    kind = Kind.INTEGER;
                }
            } else {
                kind = Kind.IDENTIFIER;
            }
            return new Token(kind, startPos, charIndex + 1);
        }
        return null;
    }

    public static void trace(String source, PrintStream out) {
        SExpression.walk(source, new SExpression.Visitor() {
            int indent;

            @Override
            public void onOpen(String operator, Supplier<String> arguments) {
                print(System.lineSeparator());
                for (int i = 0; i < indent; i++) {
                    print("  ");
                }
                print("(");
                print(operator);
                indent++;
            }

            @Override
            public void onIdentifier(String literal) {
                print(" ");
                print(literal);
            }

            @Override
            public void onInteger(int number) {
                print(" ");
                print(String.valueOf(number));
            }

            @Override
            public void onDouble(double number) {
                print(" ");
                print(String.valueOf(number));
            }

            @Override
            public void onString(String string) {
                print(" \"");
                print(string);
                print("\"");
            }

            @Override
            public void onClose(String operator, int startIndex, int endIndex) {
                print(")");
                indent--;
            }

            void print(String text) {
                out.print(text);
            }
        });
    }

    public interface Visitor {

        /**
         * Invoked when an SExpression was opened.
         * 
         * @param operation   the name of the operation used
         * @param identifiers suppliers that allows to consumer identifiers depending on
         *                    operation
         */
        void onOpen(String operation, Supplier<String> identifiers);

        void onIdentifier(String value);

        void onInteger(int value);

        void onDouble(double value);

        void onString(String value);

        void onClose(String operation, int startIndex, int length);
    }

    record Token(Kind kind, int start, int end) {

        String text(String source) {
            return source.substring(start, end);
        }

    }

    enum Kind {
        STRING, IDENTIFIER, INTEGER, DOUBLE, OPEN, CLOSE
    }

}
