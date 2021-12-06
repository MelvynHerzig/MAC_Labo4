package ch.heigvd.iict.mac.evaluation;

import org.apache.lucene.analysis.Analyzer;

public class NamedAnalyzer {
    private final String analyzerName;
    private final Analyzer analyzer;

    public NamedAnalyzer(String analyzerName, Analyzer analyzer) {
        this.analyzer = analyzer;
        this.analyzerName = analyzerName;
    }

    public String getAnalyzerName() {
        return analyzerName;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }
}
