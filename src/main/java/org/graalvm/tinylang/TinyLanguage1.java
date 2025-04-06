package org.graalvm.tinylang;

import java.util.function.Supplier;

import org.graalvm.tinylang.SExpression.Visitor;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

//Language supporting test/program1.tiny
//@TruffleLanguage.Registration(id = "tiny")
//@ProvidedTags({ RootTag.class, RootBodyTag.class, StatementTag.class, AlwaysHalt.class })
public final class TinyLanguage1 extends TruffleLanguage<Env> {

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        TinyRootNode1 parsedRoot = parse(this, request.getSource()).getNode(0);
        System.out.println(parsedRoot.dump());
        return parsedRoot.getCallTarget();
    }

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected boolean patchContext(Env context, Env newEnv) {
        return true;
    }

    public static BytecodeRootNodes<TinyRootNode1> parse(TinyLanguage1 language, Source source) {
        return TinyRootNode1Gen.create(language, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();

            SExpression.walk(source.getCharacters().toString(), new Visitor() {

                @Override
                public void onOpen(String operator, Supplier<String> arguments) {
                    switch (operator) {
                    case "add":
                        b.beginAdd();
                        break;
                    }
                }

                @Override
                public void onIdentifier(String value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void onInteger(int value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onDouble(double value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onString(String value) {
                    b.emitLoadConstant(value);
                }

                @Override
                public void onClose(String operator, int startIndex, int length) {
                    switch (operator) {
                    case "add":
                        b.endAdd();
                        break;
                    }
                }
            });

            b.endRoot();
        });
    }

    @GenerateBytecode(languageClass = TinyLanguage1.class, 
            enableBlockScoping = false, 
            boxingEliminationTypes = {int.class})
    public static abstract class TinyRootNode1 extends RootNode implements BytecodeRootNode {

        protected TinyRootNode1(TinyLanguage1 language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        String name;

        @Operation
        static final class Add {
            @Specialization
            static int doInt(int a, int b) {
                return a + b;
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        // TODO implement language here

    }
}
