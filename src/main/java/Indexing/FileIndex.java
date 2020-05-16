package Indexing;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import javax.xml.xpath.XPath;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileIndex {

    private static Path baseDirectory;
    private IndexSearcher searcher;

    public FileIndex(String path) {
        baseDirectory = Paths.get(path);
        createIndex();
    }

    /**
     * Creates a lucene index using the constant file name at the constant file path.
     */
    private void createIndex() {
        try {
            Directory dir = FSDirectory.open(baseDirectory);

            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            IndexWriter writer = new IndexWriter(dir, iwc);
            writer.deleteAll();
            writer.commit();

            ArrayList<Document> pcmFiles = indexPCMFiles(baseDirectory);

            for (Document doc : pcmFiles) {
                writer.addDocument(doc);
            }

            writer.commit();
            writer.close();

            IndexReader reader = DirectoryReader.open(FSDirectory.open(baseDirectory));
            searcher = new IndexSearcher(reader);

        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private ArrayList<Document> indexPCMFiles(Path directoryPath) {
        ArrayList<Document> indexedDocs = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directoryPath)) {
            paths.forEach((path -> indexedDocs.add(pcmToDocument(path))));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return indexedDocs;
    }

    private Document pcmToDocument(Path filePath) {
        Document doc = new Document();

        for (int i = baseDirectory.getNameCount(); i < filePath.getNameCount(); i++) {
            String pathElement = filePath.getName(i).toString();
            if (!pathElement.contains("."))
                doc.add(new TextField("folder", pathElement, Field.Store.YES));
            else
                doc.add(new TextField("name", pathElement, Field.Store.YES));
        }
        return doc;
    }

    /**
     * Searches the lucene index for the given phrase then returns the results as a string.
     * @param clientQuery The inexact initial query text
     * @param folderName The name of the folder to search in. Null if not searching for a folder.
     * @return The result of the parsed query
     */
    public String parseInexactQuery(@NotNull String clientQuery, String folderName) {

        StringBuilder topResults = new StringBuilder();
        ArrayList<Integer> hitDocIndices = new ArrayList<>();
        ArrayList<String> filteredHits = new ArrayList<>();

        // Builds an exact query accounting for multi-word queries and leading/trailing whitespace
        try {
            if (clientQuery.endsWith(" "))
                clientQuery = clientQuery.substring(0, clientQuery.length() - 1);
            if (clientQuery.contains(" "))
                clientQuery = clientQuery.replaceAll(" ", "* AND ");

            Query query = null;
            if (folderName != null && !folderName.equals("")) {
                query = new QueryParser("name", new StandardAnalyzer()).parse(
                        "folder:" + folderName + " AND name:" + clientQuery + "*");
            }
            else {
                query = new QueryParser("name", new StandardAnalyzer()).parse(clientQuery + "*");
            }
            System.out.println("Query: " + query.toString());
            ScoreDoc[] rawResults = searcher.search(query, 1000).scoreDocs;
            for (ScoreDoc rawDoc : rawResults) {
                hitDocIndices.add(rawDoc.doc);
            }

            for (Integer rawDoc : hitDocIndices) {
                filteredHits.add(searcher.doc(rawDoc).getField("name").stringValue());
            }

            //Combines results into a string
            if (filteredHits.size() > 0) {
                for (String buildingRoom : filteredHits) {
                    topResults.append(buildingRoom).append(",");
                }
            }
            System.out.println("Found " + filteredHits.size() + " unique hits.");
        }
        catch (IOException | ParseException e) {
            System.out.println(e);
        }
        return topResults.toString().equals("") ? "No results found" : topResults.toString();
    }

    /**
     * Returns the file information of the file whose exact name is given. Returns first found file
     * if files with the same exactName exist.
     * @param exactName The exact name of the file to find
     * @return The single exact file found
     */
    public String parseExactQuery(String exactName) {
        ArrayList<Integer> hitDocIndices = new ArrayList<>();
        ArrayList<List<String>> unfilteredResults = new ArrayList<>();

        //Queries the lucene index for the exact file
        exactName = exactName.replaceAll(" ", "* AND ");
        try {
            Query query = new QueryParser("name", new StandardAnalyzer()).parse(exactName + "*");
            System.out.println("Query: " + query.toString());
            ScoreDoc[] rawResults = searcher.search(query, 1000).scoreDocs;
            for (ScoreDoc rawDoc : rawResults) {
                hitDocIndices.add(rawDoc.doc);
            }
            StringBuilder filePath = new StringBuilder();
            for (Integer rawDoc : hitDocIndices) {
                searcher.doc(rawDoc).getFields().forEach((field) ->
                        filePath.append(field.stringValue().trim()).append("/"));
            }

            return filePath.toString();

        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }
}
