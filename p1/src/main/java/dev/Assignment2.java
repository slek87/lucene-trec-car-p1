package dev;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import co.nstant.in.cbor.CborException;
import edu.unh.cs.treccar.Data;
import edu.unh.cs.treccar.read_data.DeserializeData;
import edu.unh.cs.treccar.read_data.DeserializeData.RuntimeCborException;

public class Assignment2 {
	static final String INDEX_DIR = "lucene_index/dir";
	static final String CBOR_FILE = "test200/train.test200.cbor.paragraphs";
	static final String CBOR_OUTLINE = "test200/train.test200.cbor.outlines";
	static final String OUTPUT_DIR = "output";
	static final String QRELS_FILE = "test200/train.test200.cbor.article.qrels";
	
	private IndexSearcher is = null;
	private QueryParser qp = null;
	private boolean customScore = false;

	public void indexAllParas() throws CborException, IOException {
		Directory indexdir = FSDirectory.open((new File(INDEX_DIR)).toPath());
		IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
		conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		IndexWriter iw = new IndexWriter(indexdir, conf);
		for (Data.Paragraph p : DeserializeData.iterableParagraphs(new FileInputStream(new File(CBOR_FILE)))) {
			this.indexPara(iw, p);
		}
		iw.close();
	}

	public void indexPara(IndexWriter iw, Data.Paragraph para) throws IOException {
		Document paradoc = new Document();
		paradoc.add(new StringField("paraid", para.getParaId(), Field.Store.YES));
		paradoc.add(new TextField("parabody", para.getTextOnly(), Field.Store.YES));
		iw.addDocument(paradoc);
	}

	public void doSearch(String qstring, int n) throws IOException, ParseException {
		if ( is == null ) {
			is = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIR).toPath()))));
		}
		
		if ( customScore ) {
			SimilarityBase mySimiliarity = new SimilarityBase() {
				protected float score(BasicStats stats, float freq, float docLen) {
					return freq;
				}

				@Override
				public String toString() {
					return null;
				}
			};
			is.setSimilarity(mySimiliarity);
		}

		/*
		 * The first arg of QueryParser constructor specifies which field of document to
		 * match with query, here we want to search in the para text, so we chose
		 * parabody.
		 * 
		 */
		if (qp == null) {
			qp = new QueryParser("parabody", new StandardAnalyzer());
		}

		Query q;
		TopDocs tds;
		ScoreDoc[] retDocs;
		
		System.out.println("Query: " + qstring);
		q = qp.parse(qstring);
		tds = is.search(q, n);
		retDocs = tds.scoreDocs;
		Document d;
		for (int i = 0; i < retDocs.length; i++) {
			d = is.doc(retDocs[i].doc);
			System.out.println("Doc " + i);
			System.out.println("Score " + tds.scoreDocs[i].score);
			System.out.println(d.getField("paraid").stringValue());
			System.out.println(d.getField("parabody").stringValue() + "\n");
			
		}
	}

	public void customScore(boolean custom) throws IOException {
		customScore = custom;
	}
	
	/**
	 * 
	 * @param page
	 * @param n
	 * @param filename
	 * @throws IOException
	 * @throws ParseException
	 */
	public void rankParas(Data.Page page, int n, String filename) throws IOException, ParseException {
		if ( is == null ) {
			is = new IndexSearcher(DirectoryReader.open(FSDirectory.open((new File(INDEX_DIR).toPath()))));
		}
		
		if ( customScore ) {
			SimilarityBase mySimiliarity = new SimilarityBase() {
				protected float score(BasicStats stats, float freq, float docLen) {
					return freq;
				}

				@Override
				public String toString() {
					return null;
				}
			};
			is.setSimilarity(mySimiliarity);
		}

		/*
		 * The first arg of QueryParser constructor specifies which field of document to
		 * match with query, here we want to search in the para text, so we chose
		 * parabody.
		 * 
		 */
		if (qp == null) {
			qp = new QueryParser("parabody", new StandardAnalyzer());
		}

		Query q;
		TopDocs tds;
		ScoreDoc[] retDocs;
		
		System.out.println("Query: " + page.getPageName());
		q = qp.parse(page.getPageName());
		tds = is.search(q, n);
		retDocs = tds.scoreDocs;
		Document d;
		ArrayList<String> runStringsForPage = new ArrayList<String>();
		String method = "lucene";
		if(customScore)
			method = "custom";
		for (int i = 0; i < retDocs.length; i++) {
			d = is.doc(retDocs[i].doc);
			/*
			System.out.println("Doc " + i);
			System.out.println("Score " + tds.scoreDocs[i].score);
			System.out.println(d.getField("paraid").stringValue());
			System.out.println(d.getField("parabody").stringValue() + "\n");
			*/
			
			// runFile string format $queryId Q0 $paragraphId $rank $score $teamname-$methodname
			String runFileString = page.getPageId()+" Q0 "+d.getField("paraid").stringValue()
					+" "+i+" "+tds.scoreDocs[i].score+" team2-"+method;
			runStringsForPage.add(runFileString);
		}
		
		
		FileWriter fw = new FileWriter(Assignment2.OUTPUT_DIR+"/"+filename, true);
		for(String runString:runStringsForPage)
			fw.write(runString+"\n");
		fw.close();
	}
	
	public ArrayList<Data.Page> getPageListFromPath(String path){
		ArrayList<Data.Page> pageList = new ArrayList<Data.Page>();
		try {
			FileInputStream fis = new FileInputStream(new File(path));
			for(Data.Page page: DeserializeData.iterableAnnotations(fis)) {
				pageList.add(page);
				//System.out.println(page.toString());

			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RuntimeCborException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return pageList;
	}
	public HashMap<String, ArrayList<String>> getQrelsMap(String runPath)
	{
		HashMap<String, ArrayList<String>> qRelsMap = new HashMap<String, ArrayList<String>>();
		try 
		{
			BufferedReader br = new BufferedReader(new FileReader(runPath));
			String line,qid;
			ArrayList<String> paraList;
			while((line = br.readLine())!=null)
			{
				qid = line.split(" ")[0];
				if(qRelsMap.keySet().contains(qid))
					qRelsMap.get(qid).add(line.split(" ")[2]);
				else
				{
					paraList=new ArrayList<String>();
					paraList.add(line.split(" ")[2]);
					qRelsMap.put(qid, paraList);
				}		
			}	
		} 
		catch (IOException e) 
		{
				// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return qRelsMap;
	}

	public static void main(String[] args) {
		Assignment2 a = new Assignment2();
		int topSearch = 100;
		String[] queryArr = {"power nap benefits", "whale vocalization production of sound", "pokemon puzzle league"};
		
		try {
			a.indexAllParas();
			/*
			for(String qstring:queryArr) {
				a.doSearch(qstring, topSearch);
			}
			
			System.out.println(StringUtils.repeat("=", 300));
			
			a.customScore(true);
			for(String qstring:queryArr) {
				a.doSearch(qstring, topSearch);
			}
			*/
			ArrayList<Data.Page> pagelist = a.getPageListFromPath(Assignment2.CBOR_OUTLINE);
			String runFileString = "";
			
			for(Data.Page page:pagelist){
				a.rankParas(page, 100, "result-lucene.run");
			}
			// comment to check
			a.customScore(true);
			
			for(Data.Page page:pagelist){
				a.rankParas(page, 100, "result-custom.run");
			}
			
			// Compute Precision@R
			Assignment2_3 a3 = new Assignment2_3();
			HashMap<String, Double> lucRprecMap = a3.getPageRprecMap(pagelist, a.getQrelsMap(Assignment2.QRELS_FILE), Assignment2.OUTPUT_DIR+"/result-lucene.run");
			HashMap<String, Double> customRprecMap = a3.getPageRprecMap(pagelist, a.getQrelsMap(Assignment2.QRELS_FILE), Assignment2.OUTPUT_DIR+"/result-custom.run");
			
			System.out.println("\nLucene Rprec scores\n");
			for(String p:lucRprecMap.keySet())
				System.out.println(p+" -> "+lucRprecMap.get(p).toString());
				
			
			System.out.println("\nCustom Rprec scores\n");
			for(String p:customRprecMap.keySet())
				System.out.println(p+" -> "+customRprecMap.get(p).toString());
			
			// Compute Mean Average Precision
			Assignment2_4 a4 = new Assignment2_4();
			HashMap<String, ArrayList<String>> luceneMAP = a.getQrelsMap(Assignment2.OUTPUT_DIR+"/result-lucene.run");
			HashMap<String, ArrayList<String>> customMAP = a.getQrelsMap(Assignment2.OUTPUT_DIR+"/result-custom.run");
			
			a4.Precision(customMAP, luceneMAP);
			
			System.out.println("\n\nMean Average Precision : \n");
			{
				double map = 0.0;
				for(Data.Page p:pagelist)
				{
					double ap = a4.getPrecision(p.getPageId());
					map = map + ap;
					System.out.println(p.getPageId() + " : " + ap);
				}
				System.out.println("MAP : " + (map / pagelist.size()));
			}
			
			// Compute NDCG@20
			System.out.println("\n\nNDCG : \n");
			Assignment2_5 a5 = new Assignment2_5();
			a5.initNDCG(customMAP, luceneMAP);
			for (Data.Page p:pagelist)
			{
				System.out.println(p.getPageId() + " : " + a5.getNDCG20(p.getPageId()));
			}
			
			
			
			
		} catch (CborException | IOException | ParseException e) {
			e.printStackTrace();
		}	
		
	}

}