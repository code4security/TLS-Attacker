/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package tlsattacker.fuzzer.analyzer.rules;

import tlsattacker.fuzzer.config.analyzer.IsGoodRuleConfig;
import tlsattacker.fuzzer.config.EvolutionaryFuzzerConfig;
import tlsattacker.fuzzer.graphs.BranchTrace;
import tlsattacker.fuzzer.result.MergeResult;
import tlsattacker.fuzzer.result.AgentResult;
import tlsattacker.fuzzer.testvector.TestVectorSerializer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBException;

/**
 * A rule which analyzes if the TestVector reached new codepaths and set a flag
 * in the AgentResult object accordingly.
 * 
 * @author Robert Merget - robert.merget@rub.de
 */
public class IsGoodRule extends Rule {

    /**
     * A Writer object which is used to store timing statistics
     */
    private PrintWriter outWriter;

    /**
     * BranchTrace with which other Workflows are merged
     */
    private final BranchTrace branch;

    /**
     * The number of TestVectors that this rule applied to
     */
    private int found = 0;

    /**
     * The configuration object for this rule
     */
    private IsGoodRuleConfig config;

    /**
     * A timestamp of the last seen goodTestVector
     */
    private long lastGoodTimestamp = System.currentTimeMillis();

    public IsGoodRule(EvolutionaryFuzzerConfig evoConfig) {
        super(evoConfig, "is_good.rule");
        File f = new File(evoConfig.getAnalyzerConfigFolder() + configFileName);
        if (f.exists()) {
            config = JAXB.unmarshal(f, IsGoodRuleConfig.class);
        }
        if (config == null) {
            config = new IsGoodRuleConfig();
            writeConfig(config);
        }
        this.branch = new BranchTrace();
        prepareConfigOutputFolder();
        try {
            f = new File(evoConfig.getOutputFolder() + config.getOutputFileGraph());
            if (evoConfig.isCleanStart()) {
                f.delete();
                f.createNewFile();
            }
            outWriter = new PrintWriter(new BufferedWriter(new FileWriter(f, true)));
        } catch (IOException ex) {
            LOGGER.error(
                    "AnalyzeTimeRule could not initialize the output File! Does the fuzzer have the rights to write to ",
                    ex);
        }
    }

    /**
     * The rule applies if the trace in the AgentResult contains Edges or
     * Codeblocks the rule has not seen before
     * 
     * @param result
     *            AgentResult to analyze
     * @return True if the Rule contains new Codeblocks or Edges
     */
    @Override
    public synchronized boolean applies(AgentResult result) {
        MergeResult r = null;
        r = branch.merge(result.getBranchTrace());

        if (r != null && (r.getNewBranches() > 0 || r.getNewVertices() > 0)) {
            LOGGER.debug("Found a GoodTrace:{0}", r.toString());
            return true;
        } else {
            return false;
        }

    }

    /**
     * Updates statistics and stores the TestVector. Also sets a flag in the
     * TestVector such that other rules know that this TestVector is considered
     * as good.
     * 
     * @param result
     *            AgentResult to analyze
     */
    @Override
    public synchronized void onApply(AgentResult result) {
        // Write statistics
        outWriter.println(System.currentTimeMillis() - lastGoodTimestamp);
        outWriter.flush();
        lastGoodTimestamp = System.currentTimeMillis();
        found++;
        result.setGoodTrace(true);
        // It may be that we dont want to safe good Traces, for example if
        // we execute already saved Traces
        if (evoConfig.isSerialize()) {
            File f = new File(evoConfig.getOutputFolder() + config.getOutputFolder() + result.getId());
            try {
                f.createNewFile();
                TestVectorSerializer.write(f, result.getVector());
            } catch (JAXBException | IOException E) {
                LOGGER.error(
                        "Could not write Results to Disk! Does the Fuzzer have the rights to write to "
                                + f.getAbsolutePath(), E);
            }
        }
        result.getVector().getTrace().makeGeneric();
    }

    public synchronized BranchTrace getBranchTrace() {
        return branch;
    }

    /**
     * Do nothing
     * 
     * @param result
     *            AgentResult to analyze
     */
    @Override
    public void onDecline(AgentResult result) {
        result.setGoodTrace(Boolean.FALSE);
    }

    /**
     * Generates a status report
     * 
     * @return
     */
    @Override
    public synchronized String report() {
        return "Vertices:" + branch.getVerticesCount() + " Edges:" + branch.getBranchCount() + " Good:" + found
                + " Last Good " + (System.currentTimeMillis() - lastGoodTimestamp) / 1000.0 + " seconds ago\n";
    }

    @Override
    public IsGoodRuleConfig getConfig() {
        return config;
    }

}