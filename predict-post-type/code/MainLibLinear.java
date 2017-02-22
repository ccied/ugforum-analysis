package main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import de.bwaldvogel.liblinear.SolverType;
import util.Classifier;
import util.Counter;
import util.CounterInterface;
import util.CounterMap;
import util.EnglishStemmer;
import util.EnglishStopWords;
import util.GermanStemmer;
import util.GermanStopWords;
import util.HashMapIndexer;
import util.Indexer;
import util.IntCounter;
import util.LibLinearWrapper;
import util.Pair;
import util.PriorityQueue;
import util.f;

public class MainLibLinear {
	public static String german;
	public static double bias_featID;

	public static class InputDatum {
		public List<String> initiatorText;
		public List<String> initiatorOthersText;
		public List<String> respondersText;
        
        public List<String> initiatorPOSText;
		public List<String> initiatorOthersPOSText;
		public List<String> respondersPOSText;
        
        public String label;
        public String user;
        
        public InputDatum(String threadsPath, String threadId, String userID) {
			this.initiatorText = f.readLinesHard(threadsPath+"/0-initiator"+threadId+".txt");
			//if including other text written by original poster. otherwise, comment out
			String initiatorOthersTextPath = threadsPath+"/1-initiator_others"+threadId+".txt";
			File initiatorOthersTextFile = new File(initiatorOthersTextPath);
			if (initiatorOthersTextFile.exists()) {
				this.initiatorOthersText = f.readLinesHard(initiatorOthersTextPath);
			} else {
				this.initiatorOthersText = new ArrayList<String>();
			}
			//if including other text written by responders in the thread. otherwise, comment out
			String respondersTextPath = threadsPath+"/2-responders"+threadId+".txt";
			File respondersTextFile = new File(respondersTextPath);
			if (respondersTextFile.exists()) {
				this.respondersText = f.readLinesHard(respondersTextPath);
			} else {
				this.respondersText = new ArrayList<String>();
			}
            
            //if including part of speech tagging. otherwise, comment out
           	this.initiatorPOSText = f.readLinesHard(threadsPath+"/0-initiator"+threadId+".txt.out.conll.parse");
			String initiatorOthersPOSTextPath = threadsPath+"/1-initiator_others"+threadId+".txt.out.conll.parse";
			File initiatorOthersPOSTextFile = new File(initiatorOthersPOSTextPath);
			if (initiatorOthersPOSTextFile.exists()) {
				this.initiatorOthersPOSText = f.readLinesHard(initiatorOthersPOSTextPath);
			} else {
				this.initiatorOthersPOSText = new ArrayList<String>();
			}
			String respondersPOSTextPath = threadsPath+"/2-responders"+threadId+".txt.out.conll.parse";
			File respondersPOSTextFile = new File(respondersPOSTextPath);
			if (respondersPOSTextFile.exists()) {
				this.respondersPOSText = f.readLinesHard(respondersPOSTextPath);
			} else {
				this.respondersPOSText = new ArrayList<String>();
			}
           
		}
	}
	
	public static interface FeatureExtractor {
		public CounterInterface<String> extractFeatures(InputDatum thread, int label);
	}
	
	public static class SimpleFeatureExtractor implements FeatureExtractor {

		public static final boolean USE_WORD_NGRAM_FEATURES = true;
		public static final boolean USE_CHAR_NGRAM_FEATURES = true;
        public static final boolean USE_POS_FEATURES = false;
        public static final boolean USE_BINS_WORDGRAM = false;
        public static final boolean USE_BINS_CHARGRAM = false;
		public static final boolean USE_LINE_TYPE_FEATURES = false;
        public static final boolean USE_CHAR_LENGTH_FEATURES = false;
        public static final boolean USE_TOKEN_LENGTH_FEATURES = false;
        public static final boolean USE_SENT_LENGTH_FEATURES = false;
        public static final boolean USE_RANK_FEATURES = false;
        public static final boolean USE_TOTALPOSTS_FEATURES = false;
        public static final boolean USE_REPUTATION_FEATURES = false;
        public static final int MIN_CHAR_N = 4;
		public static final int MAX_CHAR_N = 6;
		public static final boolean REMOVE_STOP_WORDS = false;
		public static final boolean MONEY = false;
		public static final boolean ISNUMFEAT = false;
		
		public static String cleanLine(String line, boolean isGerm) {
			line = line.toLowerCase().trim();

			line = line.replaceAll(",", "");
			
			// URLs
			line = line.replaceAll("\\[url[^\\]]*.", "URL ");
			line = line.replaceAll("\\[.url[^\\]]*.", "");
			line = line.replaceAll("[^ ]*[.]((com)|(org)|(net))[^ ]*", "URL URL_$1");

			// Money
			if (MONEY) {
				line = line.replaceAll("([0-9]*)[$]([0-9]*)([.][0-9]*)?", "MONEY$1$2");
				line = line.replaceAll("MONEY[0-9][0-9][0-9][0-9]+", "MONEY_four_plus");
				line = line.replaceAll("MONEY[0-9][0-9][0-9]", "MONEY_three");
				line = line.replaceAll("MONEY[0-9][0-9]", "MONEY_two");
				line = line.replaceAll("MONEY[0-9]", "MONEY_one");
			}
			
			// Numbers
			line = line.replaceAll("[0-9]", "#");

			// Separate punctuation
			line = line.replaceAll("([a-z0-9])([!?;:.+()\"])", "$1 $2 ");
			line = line.replaceAll("([!?;:.+()\"])([a-z0-9])", " $1 $2");
			line = line.replaceAll(" +", " ");

			return line;
		}
		
		public static String[] removeStopWords(String[] words, boolean isGerm) {
			
			if (isGerm) {
				List<String> result = new ArrayList<String>();
				for (String word : words) {
					if (!GermanStopWords.stopWords.contains(word.toLowerCase())) {
						result.add(word);
					}
				}
				return result.toArray(new String[0]);
			}
			
			else {
				List<String> result = new ArrayList<String>();
				for (String word : words) {
					if (!EnglishStopWords.stopWords.contains(word.toLowerCase())) {
						result.add(word);
					}
				}
				return result.toArray(new String[0]);
			}
	
		}

		public CounterInterface<String> extractFeatures(InputDatum thread, int label) {
			
			boolean isGerm = false;
			if (MainLibLinear.german.equals("true")) {
				isGerm = true;
			}
            
            //only extract features from original poster's text
			String[] textTypes = new String[] {"INIT"};
            List<String>[] texts = new List[] {thread.initiatorText};
            //extracct features from all text, including part of speech
            /*String[] textTypes = new String[] {"INIT", "INIT_POS", "INITOTHERS_POS", "INITOTHERS", "RESPONDERS", "RESPONDERS_POS"};
           	List<String>[] texts = new List[] {thread.initiatorText, thread.initiatorPOSText, thread.initiatorOthersPOSText, thread.initiatorOthersText, thread.respondersText, thread.respondersPOSText};*/
			
			CounterInterface<String> features = new Counter<String>();
			features.setCount("BIAS", 1.0);
			
			for (int t=0; t<textTypes.length; ++t) {
				String textType = textTypes[t];
				List<String> text = texts[t];
				if (!text.isEmpty()) {
                    
                    
                    if (USE_CHAR_LENGTH_FEATURES) {
                        
                        addCharLengthFeatures(features, textType, thread.label, text);
                        addCharLengthFeatures(features, "", thread.label, text);
                        
                        
                    }
                    
                    if ((textType == "INIT_POS") || (textType == "INITOTHERS_POS") || (textType == "RESPONDERS_POS")) {
                        List<List<String>> thread2 = new ArrayList<List<String>>();
                        int m = 0;
                        List<String> singleList = new ArrayList<String>();
                        thread2.add(new ArrayList<String>());
                        for (String line : text) {
                            if (line.length() <= 3) {
                                m++;
                                thread2.add(new ArrayList<String>());
                                continue;
                            }
                            else {
                                thread2.get(m).add(line);
                            }
                        }
                        
                        if (USE_POS_FEATURES) {
                            for (List<String> list : thread2) {
                                for (String line: list) {
                                    addPOSFeatures(features, textType, line, list, isGerm);
                                }
                                
                            }
                            
                        }
                        
                    }
                    
					else {
						
						if (USE_BINS_WORDGRAM) {
							addWordNGramBinFeatures(features, textType, text, isGerm);
	                        addWordNGramBinFeatures(features, "", text, isGerm);
						}
						if (USE_BINS_CHARGRAM) {
							addCharNGramBinFeatures(features, textType, text, isGerm);
	                        addCharNGramBinFeatures(features, "", text, isGerm);
						}
							for (int l=0; l<text.size(); ++l) {
								String line = text.get(l);
								if (line.trim().equals("")) continue;
								line = cleanLine(line, isGerm);
								String lineType = (textType.equals("INIT") && l == 0 ? "TITLE" : "BODY");
						
								if (USE_WORD_NGRAM_FEATURES) {
									if (USE_LINE_TYPE_FEATURES) {
										addWordNGramFeatures(features, lineType+"_"+textType, line, isGerm);
										addWordNGramFeatures(features, lineType, line, isGerm);
									}
									addWordNGramFeatures(features, textType, line, isGerm);
									addWordNGramFeatures(features, "", line, isGerm);
								}
						
								if (USE_CHAR_NGRAM_FEATURES) {
									if (USE_LINE_TYPE_FEATURES) {
										addCharNGramFeatures(features, lineType+"_"+textType, line, isGerm);
										addCharNGramFeatures(features, lineType, line, isGerm);
									}
									addCharNGramFeatures(features, textType, line, isGerm);
									addCharNGramFeatures(features, "", line, isGerm);
								}
                            
							}
                        
                    }
                }
            }
			
			return features;
		}
		
		private static void addWordNGramBinFeatures(CounterInterface<String> features, String name, List<String> text, Boolean isGerm) {
			//get the total counts, to be put into bins and feature indexer
			HashMap<String, Double> counts = new HashMap<String, Double>();
			
			for (int l=0; l<text.size(); ++l) {
				String line = text.get(l);
				if (line.trim().equals("")) continue;
				line = cleanLine(line, isGerm);
				String lineType = (name.equals("INIT") && l == 0 ? "TITLE" : "BODY");
				String[] words = line.trim().split("\\s+");
			
				if (REMOVE_STOP_WORDS) {
					words = removeStopWords(words, isGerm);
				}
			
				boolean found = false;
			
				if (isGerm && ISNUMFEAT) {
					mainloop:
						for (int i=0; i<words.length; ++i) {
							String word = words[i];
							if (!word.equals("suche")) continue;
							else {
								for (int j = i; j < words.length; ++j) {
									if (words[j].contains("#")) {
										found = true;
										String num = words[j].replaceAll("[^#]", "");
	
										//INCREMENT THE COUNTS FOR BINNING LATER
										String feat = name+"_WORDNGRAM_N2_"+word+"_"+num;
										if (counts.containsKey(feat)) {
											double val = counts.get(feat);
											counts.put(feat, val+1.0);
										}
										else counts.put(feat,1.0);
									
										String feat_stem = name+"_WORDNGRAM_N2_"+GermanStemmer.stem(word)+"_"+num;
										if (counts.containsKey(feat_stem)) {
											double val = counts.get(feat_stem);
											counts.put(feat_stem, val+1.0);
										}
										else counts.put(feat_stem,1.0);
										break mainloop;
									}	
								}
							}
						}
					}
			
				for (int i=0; i<words.length; ++i) {
					String word = words[i];
					if (isGerm && ISNUMFEAT) if (word.equals("suche") && (found==true)) continue;
				
					if (word.length() == 0) continue;
					
					//INCREMENT THE COUNTS FOR BINNING LATER
					String feat = name+"_WORDNGRAM_N1_"+word;
					if (counts.containsKey(feat)) {
						double val = counts.get(feat);
						counts.put(feat, val+1.0);
					}
					else counts.put(feat,1.0);
					
					String feat_stem = name+"_WORDNGRAM_N1_STEM_"+EnglishStemmer.stem(word);
					if (counts.containsKey(feat_stem)) {
						double val = counts.get(feat_stem);
						counts.put(feat_stem, val+1.0);
					}
					else counts.put(feat_stem,1.0);	
				}

				for (int i=0; i<words.length-1; ++i) {
					String word = words[i];				
					String nextWord = words[i+1];
			
					//INCREMENT THE COUNTS FOR BINNING LATER
					String feat = name+"_WORDNGRAM_N2_"+word+"_"+nextWord;
					if (counts.containsKey(feat)) {
						double val = counts.get(feat);
						counts.put(feat, val+1.0);
					}
					else counts.put(feat,1.0);
					
					String feat_stem = name+"_WORDNGRAM_N2_STEM_"+EnglishStemmer.stem(word)+"_"+EnglishStemmer.stem(nextWord);
					if (counts.containsKey(feat_stem)) {
						double val = counts.get(feat_stem);
						counts.put(feat_stem, val+1.0);
					}
					else counts.put(feat_stem,1.0);	
				}
			}
			
			if (!isGerm) {
				for (String feat : counts.keySet()) {
					double count = counts.get(feat);
					//Binning
					double th1 = 4.0;
					if (count < th1) {
	            		features.setCount(feat+"_BIN0", 1.0);
	            	}
					else {
	                	features.setCount(feat+"_BIN10", 1.0);
	            	}
				}
			}
			
			//System.out.println("NEW DOC");
			if (isGerm) {
				for (String feat : counts.keySet()) {
					double count = counts.get(feat);
					//Binning
					double th1 = 1.0;
					if (count <= th1) {
	            		features.setCount(feat+"_BIN0", 1.0);
	            	}
					else {
	                	features.setCount(feat+"_BIN1", 1.0);
	            	}
				}
			}
		}
		
		private static void addWordNGramFeatures(CounterInterface<String> features, 
			String name, String line, Boolean isGerm) {
			
			String[] words = line.trim().split("\\s+");
			
			if (REMOVE_STOP_WORDS) {
				words = removeStopWords(words, isGerm);
			}
			
			boolean found = false;
			
			if (isGerm && ISNUMFEAT) {
				mainloop:
				for (int i=0; i<words.length; ++i) {
					String word = words[i];
					if (!word.equals("suche")) continue;
					else {
						for (int j = i; j < words.length; ++j) {
							if (words[j].contains("#")) {
								found = true;
								String num = words[j].replaceAll("[^#]", "");
								
								features.setCount(name+"_WORDNGRAM_N2_"+word+"_"+num, 1.0);
								features.setCount(name+"_WORDNGRAM_N2_"+GermanStemmer.stem(word)+"_"+num, 1.0);
								
								break mainloop;
							}	
						}
					}
				}
			}
			
			for (int i=0; i<words.length; ++i) {
				String word = words[i];
				
				if (isGerm && ISNUMFEAT) if (word.equals("suche") && (found==true)) continue;
				
				if (word.length() == 0) continue;
				
				features.setCount(name+"_WORDNGRAM_N1_"+word, 1.0);
				if (isGerm) features.setCount(name+"_WORDNGRAM_N1_STEM_"+GermanStemmer.stem(word), 1.0);
				else features.setCount(name+"_WORDNGRAM_N1_STEM_"+EnglishStemmer.stem(word), 1.0);
			}

			for (int i=0; i<words.length-1; ++i) {
				String word = words[i];				
				String nextWord = words[i+1];
			
				features.setCount(name+"_WORDNGRAM_N2_"+word+"_"+nextWord, 1.0);
				if (isGerm) features.setCount(name+"_WORDNGRAM_N2_STEM_"+GermanStemmer.stem(word)+"_"+GermanStemmer.stem(nextWord), 1.0);
				else features.setCount(name+"_WORDNGRAM_N2_STEM_"+EnglishStemmer.stem(word)+"_"+EnglishStemmer.stem(nextWord), 1.0);
			}
			
			//uncomment to include trigrams
			/*for (int i=0; i<words.length-2; ++i) {
				String word = words[i];
				String nextWord = words[i+1];
				String nextnextWord = words[i+2];
				features.setCount(name+"_WORDNGRAM_N3_"+word+"_"+nextWord+"_"+nextnextWord, 1.0);
				if (isGerm) features.setCount(name+"_WORDNGRAM_N3_STEM_"+GermanStemmer.stem(word)+"_"+GermanStemmer.stem(nextWord)+"_"+GermanStemmer.stem(nextnextWord), 1.0);
				else features.setCount(name+"_WORDNGRAM_N3_STEM_"+EnglishStemmer.stem(word)+"_"+EnglishStemmer.stem(nextWord)+"_"+EnglishStemmer.stem(nextnextWord), 1.0);
			}*/
		}
			
		private static void addCharNGramBinFeatures(CounterInterface<String> features, String name, 
				List<String> text, Boolean isGerm) {
			boolean found = false;
			
			//get the total counts, to be put into bins and feature indexer
			HashMap<String, Double> counts = new HashMap<String, Double>();
			
			for (int l=0; l<text.size(); ++l) {
					String line = text.get(l);
				for (int n=MIN_CHAR_N; n<=MAX_CHAR_N; ++n) {
					for (int i=0; i<line.length()-MAX_CHAR_N; ++i) {
						
							String ngram = line.substring(i,i+n);
							
							//INCREMENT THE COUNTS FOR BINNING LATER
							String feat = name+"_CHARNGRAM_N"+n+"_"+ngram;
							if (counts.containsKey(feat)) {
								double val = counts.get(feat);
								counts.put(feat, val+1.0);
							}
							else counts.put(feat,1.0);
						}
					}
				}
				
				for (String feat : counts.keySet()) {
					double count = counts.get(feat);
					double th1 = 10.0;
					double th2 = 50.0;
					double th3 = 100.0;
					if (count < th1) {
		           		features.setCount(feat+"_BIN0", 1.0);
		               	features.setCount(feat+"_BIN1", 0.0);
		               	features.setCount(feat+"_BIN2", 0.0);
		               	features.setCount(feat+"_BIN3", 0.0);
		           	}
					else if ((count >= th1) && (count < th2)) {
	            		features.setCount(feat+"_BIN0", 0.0);
		               	features.setCount(feat+"_BIN1", 1.0);
	                	features.setCount(feat+"_BIN2", 0.0);
	                	features.setCount(feat+"_BIN3", 0.0);
	                }
					else if ((count >= th2) && (count < th3)) {
	            		features.setCount(feat+"_BIN0", 0.0);
		               	features.setCount(feat+"_BIN1", 0.0);
	                	features.setCount(feat+"_BIN2", 1.0);
	                	features.setCount(feat+"_BIN3", 0.0);
	                }
					else {
		           		features.setCount(feat+"_BIN0", 0.0);
		               	features.setCount(feat+"_BIN1", 0.0);
		              	features.setCount(feat+"_BIN2", 0.0);
		              	features.setCount(feat+"_BIN3", 1.0);
		           	}
				}
		}
		
		private static void addCharNGramFeatures(CounterInterface<String> features, String name, 
				String line, Boolean isGerm) {
			boolean found = false;
			
			//get the total counts, to be put into bins and feature indexer
			HashMap<String, Double> counts = new HashMap<String, Double>();
			
			int suche_pos = line.indexOf("suche");
			
			if (isGerm && ISNUMFEAT) {
				String[] words = line.trim().split("\\s+");
				if (REMOVE_STOP_WORDS) {
					words = removeStopWords(words, isGerm);
				}
				mainloop:
				for (int m=0; m<words.length; ++m) {
					String word = words[m];
					if (!word.equals("suche")) continue;
					else {
						for (int j = m; j < words.length; ++j) {
							if (words[j].contains("#")) {
								found = true;								
								break mainloop;
							}	
						}
					}
				}
			}
			
			for (int n=MIN_CHAR_N; n<=MAX_CHAR_N; ++n) {
				for (int i=0; i<line.length()-MAX_CHAR_N; ++i) {
					
					if (isGerm && ISNUMFEAT){
						if (found && (i <= suche_pos) && (suche_pos <= (i+n))) {
							i = suche_pos + 5;
							if (i < line.length()-MAX_CHAR_N) {
								String ngram = line.substring(i,i+n);
								if (ngram.equals(" ")) continue;
								
								features.setCount(name+"_CHARNGRAM_N"+n+"_"+ngram, 1.0);
							
							}
						}
						
						else {
							String ngram = line.substring(i,i+n);
							
							features.setCount(name+"_CHARNGRAM_N"+n+"_"+ngram, 1.0);
						}
						
					}
					
					else {
						String ngram = line.substring(i,i+n);
						
						features.setCount(name+"_CHARNGRAM_N"+n+"_"+ngram, 1.0);
						
					}	
				}
			}
		}
        
        private static void addPOSFeatures(CounterInterface<String> features, String name, 
        		String line, List<String> list, boolean isGerm) {
        	
        	//get the total counts, to be put into bins and feature indexer
			HashMap<String, Double> counts = new HashMap<String, Double>();
			
        	if (!isGerm) {
        		String[] lineSplit = line.split("\\s+");
        		int connection = Integer.parseInt(lineSplit[6]);
        		String newline = "";
        		String word = "";
        		if (connection == 0) {
        			word = lineSplit[1] + " " + lineSplit[7] + " (EMPTY)";
        		}
        		else {
        			newline = list.get(connection - 1).toString();
        			String[] newLineSplit = newline.split("\\s+");
        			word = lineSplit[1] + " " + lineSplit[7] + " " + newLineSplit[1];
        		}
        		features.setCount(name+"_"+word, 1.0);
        	}
            
        	else {
        		String[] lineSplit = line.split("\\s+");
        		int connection = Integer.parseInt(lineSplit[3]);
        		String newline = "";
        		String word = "";
        		if (connection == 0) {
        			word = lineSplit[1] + " " + lineSplit[2] + " (EMPTY)";
        		}
        		else {
        			newline = list.get(connection - 1).toString();
        			String[] newLineSplit = newline.split("\\s+");
        			word = lineSplit[1] + " " + lineSplit[2] + " " + newLineSplit[1];
        		}
        		
        		features.setCount(name+"_"+word, 1.0);
        	}
		}
        
        private static void addRankFeatures(CounterInterface<String> features, String name, String threadId, String userID, HashMap<String,String> allRankThreadPairs) {
            
            for (String key: allRankThreadPairs.keySet()) {
                if (key.equals(threadId)) features.setCount(name+"_RANK"+userID, 1.0);
                else {
                    String user = allRankThreadPairs.get(key);
                    features.setCount(name+"_RANK"+user, 0.0);
                }
            }            
        }
        
        private static void addTotalPostsFeatures(CounterInterface<String> features, String name, String threadId, String userID, HashMap<String,String> allTotalPostsThreadPairs) {
            
            for (String key: allTotalPostsThreadPairs.keySet()) {
                if (key.equals(threadId)) features.setCount(name+"_TOTALPOSTS"+userID, 1.0);
                else {
                    String user = allTotalPostsThreadPairs.get(key);
                    features.setCount(name+"_TOTALPOSTS"+user, 0.0);
                }
            }            
        }
        
        private static void addReputationFeatures(CounterInterface<String> features, String name, String threadId, String userID, HashMap<String,String> allReputationThreadPairs) {
            
            for (String key: allReputationThreadPairs.keySet()) {
                if (key.equals(threadId)) features.setCount(name+"_REPUTATION"+userID, 1.0);
                else {
                    String user = allReputationThreadPairs.get(key);
                    features.setCount(name+"_REPUTATION"+user, 0.0);
                }
            }   
        }
        
        private static void addCharLengthFeatures(CounterInterface<String> features, String name, 
        		String threadId, List<String> text) {
            
            int num_chars = 0;
            for (String line : text) {
                String temp = line.replaceAll("\\s+","");
                num_chars = num_chars + temp.length();
                
            }

            int bin1 = 200;
            
            if (num_chars < bin1) {
                
                features.setCount(name+"_LENGTH_CHAR_BIN0", 1.0);
                features.setCount(name+"_LENGTH_CHAR_BIN1", 0.0);

            }
            
            else {
                features.setCount(name+"_LENGTH_CHAR_BIN0", 0.0);
                features.setCount(name+"_LENGTH_CHAR_BIN1", 1.0);

            }
               
        }
    
        private static void addTokenLengthFeatures(CounterInterface<String> features, String name, String threadId, List<String> text) {
        
        int num_token = 0;
        for (String line : text) {
            String[] lineSplit = line.split("\\s+");
            num_token = num_token + lineSplit.length;
            
        }
        
        long rounded = Math.round(Math.log(num_token));

        if (num_token >= 200) {
            features.setCount(name+"_LENGTH_TOKEN_BIN0", 1.0);
        }
        else features.setCount(name+"_LENGTH_TOKEN_BIN0", 0.0);
        
        if (num_token >= 500) {
         features.setCount(name+"_LENGTH_TOKEN_BIN1", 1.0);
         }
         else features.setCount(name+"_LENGTH_TOKEN_BIN1", 0.0);
         
         if (num_token >= 100) {
         features.setCount(name+"_LENGTH_TOKEN_BIN2", 1.0);
         }
         else features.setCount(name+"_LENGTH_TOKEN_BIN2", 0.0);
         
         if (num_token >= 750) {
         features.setCount(name+"_LENGTH_TOKEN_BIN3", 1.0);
         }
         else features.setCount(name+"_LENGTH_TOKEN_BIN3", 0.0);
        
    }
        
        private static void addSentLengthFeatures(CounterInterface<String> features, String name, String threadId, List<String> text) {
            
            int num_sent = 0;
            for (String line : text) {
                int num_period = line.length() - line.replace(".", "").length();
                int num_exclamation = line.length() - line.replace("!", "").length();
                int num_question = line.length() - line.replace("?", "").length();
                num_sent += num_period + num_exclamation + num_question;
                
            }
            
            if (num_sent >= 1) {
                features.setCount(name+"_LENGTH_SENT_BIN0", 1.0);
            }
            else features.setCount(name+"_LENGTH_SENT_BIN0", 0.0);
            
            if (num_sent >= 2) {
             features.setCount(name+"_LENGTH_SENT_BIN1", 1.0);
             }
             else features.setCount(name+"_LENGTH_SENT_BIN1", 0.0);
            
             if (num_sent >= 5) {
             features.setCount(name+"_LENGTH_SENT_BIN2", 1.0);
             }
             else features.setCount(name+"_LENGTH_SENT_BIN2", 0.0);
            
            if (num_sent >= 10) {
                features.setCount(name+"_LENGTH_SENT_BIN3", 1.0);
            }
            else features.setCount(name+"_LENGTH_SENT_BIN3", 0.0);
            
        }
    
	}
	
    public static Pair<List<Pair<CounterInterface<Integer>,Integer>>, ArrayList<String>> readData(String labelsPath, 
    	String threadsPath, List<FeatureExtractor> featureExtractors, Indexer<String> featureIndexer, Indexer<String> labelIndexer) {
		List<String> labelsRaw = f.readLinesHard(labelsPath);
		// Read annotations (in Vern's canonicalised format)
		CounterMap<String,Integer> threadIdToLabelCounter = new CounterMap<String,Integer>();
        
        HashMap<String,String> threadIdToUserID = new HashMap<String,String>();
        HashSet<String> userIDS = new HashSet<>();
        
		for (String line : labelsRaw) {
			if (line.trim().equals("")) continue;
			String[] split = line.trim().split("\\s+");
			String threadId = split[2];

			// Read the format with a single property on each line
			String label = split[3];
			if (labelIndexer.contains(label)) {
				threadIdToLabelCounter.incrementCount(threadId, labelIndexer.getIndex(label), 1.0);
			}
        }
		
		// Read threads and generate feature vectors
		List<Pair<CounterInterface<Integer>,Integer>> data = new ArrayList<Pair<CounterInterface<Integer>,Integer>>();
		List<String> threadIds = new ArrayList<String>(threadIdToLabelCounter.keySet());
		Collections.shuffle(threadIds, new Random(0));
        ArrayList<String> dataOrder = new ArrayList<String>();
                    
		System.out.println(threadIds.size() + " instances");
		int line = 0;
        CounterInterface<Integer> features;
		for (String threadId : threadIds) {
			line++;
			if (MainLibLinear.german.equals("true")) {
				if (line%100 == 0) System.out.println("line : " + line);
			}
			else if (line%1000 == 0) System.out.println("line : " + line);
            String userID = threadIdToUserID.get(threadId);
            InputDatum thread = new InputDatum(threadsPath, threadId, userID);
			int label = threadIdToLabelCounter.getCounter(threadId).argMax();

			//ORIGINAL
			features = new IntCounter();
			for (FeatureExtractor featExtractor : featureExtractors) {
                //pass the training label value
				CounterInterface<String> feats = featExtractor.extractFeatures(thread, label);
				for (String featName : feats.keySet()) {
					double featValue = feats.getCount(featName);
					int featId = featureIndexer.getIndex(featName);
					features.incrementCount(featId, featValue);
				}
			}
        
			boolean user = false;
        if (user == true) {
        	CounterInterface<String> features_temp = new Counter<String>();
        for (String uID : userIDS) {
            String trueUID = threadIdToUserID.get(threadId);
                    
            if (trueUID.equals(uID)) {
                features_temp.setCount("USERID_"+uID, 1.0);
                }
                    
            else {
                features_temp.setCount("USERID_"+uID, 0.0);
                }
            }
            
            for (String featName : features_temp.keySet()) {
                double featValue = features_temp.getCount(featName);
                int featId = featureIndexer.getIndex(featName);
                
                features.incrementCount(featId, featValue);
            }}

			data.add(Pair.makePair(features,label));
      dataOrder.add(threadId);
        }
		
		return Pair.makePair(data,dataOrder);
	}
    	    
	public static void main(String[] args) throws UnsupportedEncodingException {
		if (args.length < 6) {
			System.out.println("Arguments are: <boolean train/test same/difff> <threadsPath> <labelsPath> <testFraction> <labelType1>:<labelType2>:... <germanBool> [Optional <trainSize>]");
			return;
		}
		
		List<Pair<CounterInterface<Integer>,Integer>> trainData = null;
		List<Pair<CounterInterface<Integer>,Integer>> testData = null;
		String[] labels = null;
		Indexer<String> labelIndexer = null;
		List<String> testThreads = null;
		List<String> trainThreads = null;
		Indexer<String> featureIndexer = new HashMapIndexer<String>();
		
		
		// TRAIN AND TEST ON SAME DATASET
		if (args[0].equals("same"))
		{String threadsPath = args[1];
		String labelsPath = args[2];
		double testFraction = Double.parseDouble(args[3]);
		labels = args[4].split(":");
		int trainSize = Integer.MAX_VALUE;
		if (args.length == 7) {
			trainSize = Integer.parseInt(args[6]);
		}
		MainLibLinear.german = args[5];
		
		// label types
		labelIndexer = new HashMapIndexer<String>();
		for (String label : labels) {
			labelIndexer.getIndex(label);
		}
		labelIndexer.lock();
		
		// feature extractors
		List<FeatureExtractor> featureExtractors = new ArrayList<FeatureExtractor>();
		featureExtractors.add(new SimpleFeatureExtractor());
		//featureExtractors.add(new BOWFeatureExtractor());
	
		// data
        Pair<List<Pair<CounterInterface<Integer>,Integer>>, ArrayList<String>> all = readData(labelsPath, threadsPath, featureExtractors, featureIndexer, labelIndexer);
        List<Pair<CounterInterface<Integer>,Integer>> allData = all.getFirst();
        ArrayList<String> allThreads = all.getSecond();
        int testSize = (int) (testFraction * allData.size());
        trainSize = Math.min(trainSize, allData.size() - testSize);
        System.out.println("train size: "+trainSize);
        System.out.println("test size: "+testSize);
        trainData = allData.subList(0, trainSize);
        testData = allData.subList((allData.size() - testSize), allData.size());
        featureIndexer.lock();
        trainThreads = allThreads.subList(0, trainSize);
        testThreads = allThreads.subList((allThreads.size() - testSize), allThreads.size());
		}
		
		// TRAIN AND TEST ON DIFFERENT DATASETS
		if (args[0].equals("diff"))
        {String threadsPathTrain = args[1];
		String labelsPathTrain = args[2];
		String threadsPathTest = args[3];
		String labelsPathTest = args[4];
		labels = args[5].split(":");
		MainLibLinear.german = args[6];
		
		//// label types
		labelIndexer = new HashMapIndexer<String>();
		for (String label : labels) {
			labelIndexer.getIndex(label);
		}
		labelIndexer.lock();
		
		//// feature extractors
		List<FeatureExtractor> featureExtractors = new ArrayList<FeatureExtractor>();
		featureExtractors.add(new SimpleFeatureExtractor());
		//featureExtractors.add(new BagOfCharsFeatureExtractor());
		
		//// data
		featureIndexer = new HashMapIndexer<String>();
        System.out.println("reading data ...");
        Pair<List<Pair<CounterInterface<Integer>,Integer>>, ArrayList<String>> trainTemp = readData(labelsPathTrain, threadsPathTrain, featureExtractors, featureIndexer, labelIndexer);
		trainData = trainTemp.getFirst();
		//trainData = filterData(trainData, 0.10);
        
        Pair<List<Pair<CounterInterface<Integer>,Integer>>, ArrayList<String>> testTemp = readData(labelsPathTest, threadsPathTest, featureExtractors, featureIndexer, labelIndexer);
		testData = testTemp.getFirst();
		//testData = filterData(testData, 0.10);
		System.out.println("data read");
		featureIndexer.lock();
        testThreads = testTemp.getSecond();
        
		System.out.println("train size: "+trainData.size());
        System.out.println("test size: "+testData.size());
        
        }
        
		// print label counts
		Counter<Integer> trainLabelCounts = new Counter<Integer>();
		for (Pair<CounterInterface<Integer>,Integer> datum : trainData) {
			trainLabelCounts.incrementCount(datum.getSecond(), 1.0);
		}
		System.out.println();
		System.out.println("train label counts:");
		for (String label : labels) {
			System.out.println(label + " : " +  trainLabelCounts.getCount(labelIndexer.getIndex(label)));
		}
		
		Counter<Integer> testLabelCounts = new Counter<Integer>();
		for (Pair<CounterInterface<Integer>,Integer> datum : testData) {
			testLabelCounts.incrementCount(datum.getSecond(), 1.0);
		}
		System.out.println();
		System.out.println("test label counts:");
		for (String label : labels) {
			System.out.println(label + " : " +  testLabelCounts.getCount(labelIndexer.getIndex(label)));
		}
		System.out.println();

		// classifier
		double C = 0.0002;
		double eps = 1e-7;
		//SolverType solverType = SolverType.MCSVM_CS;
        SolverType solverType = SolverType.L2R_L2LOSS_SVC;
		Classifier classifier = new LibLinearWrapper(solverType, C, eps);
		
		System.out.println("training classifier:");
		classifier.train(trainData);
		
		// classifier output on train
		{
			double correct = 0.0;
			double total = 0.0;
			for (Pair<CounterInterface<Integer>,Integer> datum : trainData) {
				int goldLabel = datum.getSecond();
				CounterInterface<Integer> features = datum.getFirst();
				int predictedLabel = classifier.predict(features);
				if (predictedLabel == goldLabel) {
					correct++;
				}

				total++;
			}
			System.out.printf("train system acc: %.4f\n", correct / total);
		}
		
		//classifier output on test
		{
      int cid = 0;
			double correct = 0.0;
			double total = 0.0;
			Counter<Integer> labelCorrect = new Counter<Integer>();
			Counter<Integer> labelTotal = new Counter<Integer>();
			for (Pair<CounterInterface<Integer>,Integer> datum : testData) {
				int goldLabel = datum.getSecond();
				CounterInterface<Integer> features = datum.getFirst();
				int predictedLabel = classifier.predict(features);
				if (predictedLabel == goldLabel) {
					labelCorrect.incrementCount(goldLabel, 1.0);
					correct++;
				} 
				
				else {
					System.out.printf("thread %s Wrong: %d instead of %d\n", testThreads.get(cid), predictedLabel, goldLabel);
				}
				
				labelTotal.incrementCount(goldLabel, 1.0);
				total++;
                
                cid++;
            }
            
            System.out.println("TOTAL : " + total);
            
			System.out.printf("test system acc: %.4f\n", correct / total);
			System.out.println("acc by label:");
			for (int label : labelTotal.keySet()) {
				System.out.printf("%s : %.4f\n", labelIndexer.getObject(label), labelCorrect.getCount(label)/labelTotal.getCount(label));
				System.out.println(labelCorrect.getCount(label) + " out of " + labelTotal.getCount(label));
			}
			System.out.println();
		}
		
		//print biggest feature weights
		int numFeaturesToPrint = 100;
		Map<Integer,CounterInterface<Integer>> weights = classifier.getWeights();
		for (Integer label : weights.keySet()) {
			CounterInterface<Integer> labelWeights = weights.get(label);
			Counter<Integer> sorter = new Counter<Integer>(); 
			sorter.incrementAll(labelWeights);
			PriorityQueue<Integer> maxQueue = sorter.asPriorityQueue();
			PriorityQueue<Integer> minQueue = sorter.asMinPriorityQueue();
			
			System.out.println("\n\nlabel: "+labelIndexer.getObject(label));
			System.out.println("\nmax feat weights:");
			for (int i=0; i<numFeaturesToPrint; ++i) {
                if (maxQueue.isEmpty()) break;
				int feat = maxQueue.next();
                 PrintStream out = new PrintStream(System.out, true, "UTF-8");
                out.print(featureIndexer.getObject(feat));
                System.out.println(" : "+labelWeights.getCount(feat));
				//System.out.println(featureIndexer.getObject(feat)+" : "+labelWeights.getCount(feat));
			}
			System.out.println("\nmin feat weights:");
			for (int i=0; i<numFeaturesToPrint; ++i) {
				if (minQueue.isEmpty()) break;
				int feat = minQueue.next();
                PrintStream out = new PrintStream(System.out, true, "UTF-8");
                out.print(featureIndexer.getObject(feat));
                System.out.println(" : "+labelWeights.getCount(feat));
				//System.out.println(featureIndexer.getObject(feat)+" : "+labelWeights.getCount(feat));
			}
		}
		
	}
	
	public static List<Pair<CounterInterface<Integer>,Integer>> filterData(List<Pair<CounterInterface<Integer>,Integer>> trainData, double frac) {
		System.out.println("trainData size before filterData : " + trainData.size());
		Counter<Integer> labelCounts = new Counter<Integer>();
		for (Pair<CounterInterface<Integer>,Integer> datum : trainData) {
			labelCounts.incrementCount(datum.getSecond(), 1.0);
		}
		int maxLabel = labelCounts.argMax();
		System.out.println("maxLabel : " + maxLabel);
		Random rand = new Random(0);
		List<Pair<CounterInterface<Integer>,Integer>> result = new ArrayList<Pair<CounterInterface<Integer>,Integer>>();
		for (Pair<CounterInterface<Integer>,Integer> datum : trainData) {
			int label = datum.getSecond();
			if (label != maxLabel || rand.nextDouble() < frac) {
				result.add(datum);
			}
		}
		System.out.println("trainData size after filterData : " + result.size());
		return result;
	}
	
}
