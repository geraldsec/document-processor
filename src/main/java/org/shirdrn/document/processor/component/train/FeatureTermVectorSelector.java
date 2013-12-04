package org.shirdrn.document.processor.component.train;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.shirdrn.document.processor.common.AbstractComponent;
import org.shirdrn.document.processor.common.Context;
import org.shirdrn.document.processor.common.Term;
import org.shirdrn.document.processor.utils.SortUtils;

public class FeatureTermVectorSelector extends AbstractComponent {

	private static final Log LOG = LogFactory.getLog(FeatureTermVectorSelector.class);
	private final int keptTermCountEachLabel;
	
	public FeatureTermVectorSelector(Context context) {
		super(context);
		keptTermCountEachLabel = context.getConfiguration().getInt("processor.each.label.kept.term.count", 3000);
	}

	@Override
	public void fire() {
		Iterator<Entry<String, Integer>> iter = context.getVectorMetadata().labelVectorMapIterator();
		while(iter.hasNext()) {
			Entry<String, Integer> entry = iter.next();
			// for each label, compute CHI vector
			LOG.info("Compute CHI for: label=" + entry.getKey());
			processOneLabel(entry.getKey());
		}
		// sort and select CHI vectors
		Iterator<Entry<String, Map<String, Term>>> chiIter = 
				context.getVectorMetadata().chiLabelToWordsVectorsIterator();
		while(chiIter.hasNext()) {
			Entry<String, Map<String, Term>> entry = chiIter.next();
			String label = entry.getKey();
			LOG.info("Sort CHI terms for: label=" + label + ", termCount=" + entry.getValue().size());
			Entry<String, Term>[] a = sort(entry.getValue());
			for (int i = 0; i < Math.min(a.length, keptTermCountEachLabel); i++) {
				Entry<String, Term> termEntry = a[i];
				// merge CHI terms for all labels
				context.getVectorMetadata().addChiMergedTerm(termEntry.getKey(), termEntry.getValue());
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Entry<String, Term>[] sort(Map<String, Term> terms) {
		Entry<String, Term>[] a = new Entry[terms.size()];
		a = terms.entrySet().toArray(a);
		SortUtils.heapSort(a, true, keptTermCountEachLabel);
		return a;
	}

	private void processOneLabel(String label) {
		Iterator<Entry<String, Map<String, Set<String>>>> iter = 
				context.getVectorMetadata().invertedTableIterator();
		while(iter.hasNext()) {
			Entry<String, Map<String, Set<String>>> entry = iter.next();
			String word = entry.getKey();
			Map<String, Set<String>> labelledDocs = entry.getValue();
			
			// A: doc count containing the word in this label
			int docCountContainingWordInLabel = 0;
			if(labelledDocs.get(label) != null) {
				docCountContainingWordInLabel = labelledDocs.get(label).size();
			}
			
			// B: doc count containing the word not in this label
			int docCountContainingWordNotInLabel = 0;
			Iterator<Entry<String, Set<String>>> labelledIter = 
					labelledDocs.entrySet().iterator();
			while(labelledIter.hasNext()) {
				Entry<String, Set<String>> labelledEntry = labelledIter.next();
				String tmpLabel = labelledEntry.getKey();
				if(!label.equals(tmpLabel)) {
					docCountContainingWordNotInLabel += entry.getValue().size();
				}
			}
			
			// C: doc count not containing the word in this label
			int docCountNotContainingWordInLabel = 
					getDocCountNotContainingWordInLabel(word, label);
			
			// D: doc count not containing the word not in this label
			int docCountNotContainingWordNotInLabel = 
					getDocCountNotContainingWordNotInLabel(word, label);
			
			// compute CHI value
			int N = context.getVectorMetadata().getTotalDocCount();
			int A = docCountContainingWordInLabel;
			int B = docCountContainingWordNotInLabel;
			int C = docCountNotContainingWordInLabel;
			int D = docCountNotContainingWordNotInLabel;
			double chi = (double) N*(A*D-B*C) / (A+C)*(A+B)*(B+D)*(C+D);
			Term term = new Term(word);
			term.setChi(chi);
			context.getVectorMetadata().addChiTerm(label, word, term);
		}
	}

	private int getDocCountNotContainingWordInLabel(String word, String label) {
		int count = 0;
		Iterator<Entry<String,Map<String,Map<String,Term>>>> iter = 
				context.getVectorMetadata().termTableIterator();
		while(iter.hasNext()) {
			Entry<String,Map<String,Map<String,Term>>> entry = iter.next();
			String tmpLabel = entry.getKey();
			// in this label
			if(tmpLabel.equals(label)) {
				Map<String, Map<String, Term>> labelledDocs = entry.getValue();
				for(Entry<String, Map<String, Term>> docEntry : labelledDocs.entrySet()) {
					// not containing this word
					if(!docEntry.getValue().containsKey(word)) {
						++count;
					}
				}
				break;
			}
		}
		return count;
	}
	
	private int getDocCountNotContainingWordNotInLabel(String word, String label) {
		int count = 0;
		Iterator<Entry<String,Map<String,Map<String,Term>>>> iter = 
				context.getVectorMetadata().termTableIterator();
		while(iter.hasNext()) {
			Entry<String,Map<String,Map<String,Term>>> entry = iter.next();
			String tmpLabel = entry.getKey();
			// not in this label
			if(!tmpLabel.equals(label)) {
				Map<String, Map<String, Term>> labelledDocs = entry.getValue();
				for(Entry<String, Map<String, Term>> docEntry : labelledDocs.entrySet()) {
					// not containing this word
					if(!docEntry.getValue().containsKey(word)) {
						++count;
					}
				}
			}
		}
		return count;
	}

}
