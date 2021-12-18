package ch.heigvd.iict.mac.evaluation;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class Evaluation {
    private static void readFile(String filename, Function<String, Void> parseLine)
            throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(filename),
                        StandardCharsets.UTF_8)
        )) {
            String line = br.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    parseLine.apply(line);
                }
                line = br.readLine();
            }
        }
    }

    /*
     * Reading CACM queries and creating a list of queries.
     */
    private static List<String> readingQueries() throws IOException {
        final String QUERY_SEPARATOR = "\t";

        List<String> queries = new ArrayList<>();

        readFile("evaluation/query.txt", line -> {
            String[] query = line.split(QUERY_SEPARATOR);
            queries.add(query[1]);
            return null;
        });
        return queries;
    }

    /*
     * Reading stopwords
     */
    private static List<String> readingCommonWords() throws IOException {
        List<String> commonWords = new ArrayList<>();

        readFile("common_words.txt", line -> {
            commonWords.add(line);
            return null;
        });
        return commonWords;
    }


    /*
     * Reading CACM qrels and creating a map that contains list of relevant
     * documents per query.
     */
    private static Map<Integer, List<Integer>> readingQrels() throws IOException {
        final String QREL_SEPARATOR = ";";
        final String DOC_SEPARATOR = ",";

        Map<Integer, List<Integer>> qrels = new HashMap<>();

        readFile("evaluation/qrels.txt", line -> {
            String[] qrel = line.split(QREL_SEPARATOR);
            int query = Integer.parseInt(qrel[0]);

            List<Integer> docs = qrels.get(query);
            if (docs == null) {
                docs = new ArrayList<>();
            }

            String[] docsArray = qrel[1].split(DOC_SEPARATOR);
            for (String doc : docsArray) {
                docs.add(Integer.parseInt(doc));
            }

            qrels.put(query, docs);
            return null;
        });
        return qrels;
    }

    public static void main(String[] args) throws IOException {
        ///
        /// Reading queries and queries relations files
        ///
        List<String> queries = readingQueries();
        System.out.println("Number of queries: " + queries.size());

        Map<Integer, List<Integer>> qrels = readingQrels();
        System.out.println("Number of qrels: " + qrels.size());

        double avgQrels = 0.0;
        for (List<Integer> rels : qrels.values()) {
            avgQrels += rels.size();
        }
        avgQrels /= qrels.size();
        System.out.println("Average number of relevant docs per query: " + avgQrels);

        List<String> commonWords = readingCommonWords();

        ///
        ///  Part I - Create the analyzers
        ///
        var analyzers = List.of(
              new NamedAnalyzer("Standard", new StandardAnalyzer()),
              new NamedAnalyzer("Whitespace", new WhitespaceAnalyzer()),
              new NamedAnalyzer("English", new EnglishAnalyzer()),
              new NamedAnalyzer("English with custom stopwords",
                      new EnglishAnalyzer(new CharArraySet(commonWords, true)))
        );

        for(NamedAnalyzer na : analyzers) {
            String analyzerName = na.getAnalyzerName();
            Analyzer analyzer = na.getAnalyzer();

            if (analyzer == null) {
                System.err.printf("The analyzer \"%s\" has not been implemented%n", analyzerName);
            } else {
                System.out.printf("%n=== Using analyzer: %s%n", analyzerName);

                ///
                ///  Part I - Create the index
                ///
                LabIndex labIndex = new LabIndex(analyzer);
                labIndex.index("documents/cacm.txt");
                evaluateMetrics(labIndex, queries, qrels);
            }
        }
    }

    private static void evaluateMetrics(LabIndex labIndex, List<String> queries, Map<Integer, List<Integer>> qrels) {
        ///
        ///  Part II and III:
        ///  Execute the queries and assess the performance of the
        ///  selected analyzer using performance metrics like F-measure,
        ///  precision, recall,...
        ///


        // Variables used for query set.
        int queryNumber = 0;
        int totalRelevantDocs = 0;
        int totalRetrievedDocs = 0;
        int totalRetrievedRelevantDocs = 0;
        double avgPrecision = 0.0;
        double avgRPrecision = 0.0;
        double avgRecall = 0.0;
        double meanAveragePrecision = 0.0;
        double fMeasure = 0.0;

        // average precision at the 11 recall levels (0,0.1,0.2,...,1) over all queries
        double[] avgPrecisionAtRecallLevels = createZeroedRecalls();

        // For each query
        for (String query : queries) {

            // Getting query retrieved documents
            List<Integer> queryResults = labIndex.search(query);
            // Getting query really relevant documents
            List<Integer> qrelResults = ( qrels.get(queryNumber + 1) == null ? new LinkedList<>() : qrels.get(queryNumber + 1) );


            int queryRetrievedDocs = queryResults.size();
            int queryRelevantDocs = qrelResults.size();
            int queryRetrievedRelevantDocs = 0;

            double queryAveragePrecision = 0;
            double queryRPrecision = 0;
            double[] queryPrecisionAtRecallLevels = createZeroedRecalls();

            // For each retrieved documents.
            for( int retrievedDocI = 0 ; retrievedDocI < queryResults.size() ; retrievedDocI++)
            {
                // AP
                // Is the retrieved document a relevant document ?
                if(qrelResults.contains(queryResults.get(retrievedDocI))) {
                    queryRetrievedRelevantDocs++;
                    queryAveragePrecision += ( (double)queryRetrievedRelevantDocs /  (retrievedDocI + 1.));
                }

                // R-Precision
                // When we have "parsed" as much retrieved documents as the number of relevant documents
                if ( queryRelevantDocs > 0 && retrievedDocI == (queryRelevantDocs - 1) ) {
                    queryRPrecision = (double) queryRetrievedRelevantDocs / (double) queryRelevantDocs;
                }

                // Interpolated precision
                // Recall at the time of the current document
                double localRecall = ( queryRelevantDocs == 0 ? 0 : (double) queryRetrievedRelevantDocs / (double)queryRelevantDocs);
                // Precision at the time of the current document
                double localPrecision = (double) queryRetrievedRelevantDocs / ( retrievedDocI + 1.);
                for( int i = 0; i <= 10 && (((double) i / 10 ) <= localRecall); i++) {
                    if(localPrecision > queryPrecisionAtRecallLevels[i]) {
                        queryPrecisionAtRecallLevels[i] = localPrecision;
                    }
                }
            }
            // If no relevant documents, we set query AP to 0 else the usual formula is used.
            meanAveragePrecision += ( queryRelevantDocs == 0 ? 0 : queryAveragePrecision / queryRelevantDocs);

            totalRetrievedDocs += queryRetrievedDocs;
            totalRelevantDocs += queryRelevantDocs;
            totalRetrievedRelevantDocs += queryRetrievedRelevantDocs;

            // If there is no retrieved documents, precision = 0 else precision = usual formula
            avgPrecision += ( queryRetrievedDocs == 0 ? 0 : (double) queryRetrievedRelevantDocs / (double) queryRetrievedDocs );
            // If there is no relevant documents, recall = 0 else recall = usual formula
            avgRecall += ( queryRelevantDocs  == 0 ? 0 : (double) queryRetrievedRelevantDocs / (double) queryRelevantDocs );

            avgRPrecision += queryRPrecision;

            for(int i = 0; i <= 10; i++) {
                avgPrecisionAtRecallLevels[i] += queryPrecisionAtRecallLevels[i];
            }

            queryNumber++;
        }

        avgPrecision /= queries.size();
        avgRecall /= queries.size();

        fMeasure = (2 * avgPrecision * avgRecall) / (avgPrecision + avgRecall);

        avgRPrecision /= queries.size();
        meanAveragePrecision /= queries.size();

        for(int i = 0; i <= 10; i++) {
            avgPrecisionAtRecallLevels[i] /= queries.size();
        }

        ///
        ///  Part IV - Display the metrics
        ///
        displayMetrics(totalRetrievedDocs, totalRelevantDocs,
                totalRetrievedRelevantDocs, avgPrecision, avgRecall, fMeasure,
                meanAveragePrecision, avgRPrecision,
                avgPrecisionAtRecallLevels);
    }

    private static void displayMetrics(
            int totalRetrievedDocs,
            int totalRelevantDocs,
            int totalRetrievedRelevantDocs,
            double avgPrecision,
            double avgRecall,
            double fMeasure,
            double meanAveragePrecision,
            double avgRPrecision,
            double[] avgPrecisionAtRecallLevels
    ) {
        System.out.println("Number of retrieved documents: " + totalRetrievedDocs);
        System.out.println("Number of relevant documents: " + totalRelevantDocs);
        System.out.println("Number of relevant documents retrieved: " + totalRetrievedRelevantDocs);

        System.out.println("Average precision: " + avgPrecision);
        System.out.println("Average recall: " + avgRecall);

        System.out.println("F-measure: " + fMeasure);

        System.out.println("MAP: " + meanAveragePrecision);

        System.out.println("Average R-Precision: " + avgRPrecision);

        System.out.println("Average precision at recall levels: ");
        for (int i = 0; i < avgPrecisionAtRecallLevels.length; i++) {
            System.out.printf("\t%s: %s%n", i, avgPrecisionAtRecallLevels[i]);
        }
    }

    private static double[] createZeroedRecalls() {
        double[] recalls = new double[11];
        Arrays.fill(recalls, 0.0);
        return recalls;
    }
}
