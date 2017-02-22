package edu.berkeley.forum.util

import edu.berkeley.nlp.futile.util.Logger

object GrepComparisonTest {

  
  def main(args: Array[String]) {
    val darkodeExs = SemanticCategoryClassifier.loadSemCatExsDarkode
    val hackforumsExs = SemanticCategoryClassifier.loadSemCatExsHackforums
    val allExs = darkodeExs ++ hackforumsExs
    val cryptSetGrep = allExs.filter(_.words.flatten.map(_.toLowerCase.contains("crypt")).foldLeft(false)(_ || _)).map(_.fileId).toSet
//    val accountsSetGrep = allExs.filter(_.words.flatten.map(_.toLowerCase.contains("accounts")).foldLeft(false)(_ || _)).map(_.fileId).toSet
//    val accountSetGrep = allExs.filter(_.words.flatten.map(_.toLowerCase.contains("account")).foldLeft(false)(_ || _)).map(_.fileId).toSet -- accountsSetGrep
    
    val accountsSetGrep = allExs.filter(_.words.flatten.map(word => word.toLowerCase.contains("accounts") || word.toLowerCase.contains("emails")).foldLeft(false)(_ || _)).map(_.fileId).toSet
    val accountSetGrep = allExs.filter(_.words.flatten.map(word => word.toLowerCase.contains("account") || word.toLowerCase.contains("email")).foldLeft(false)(_ || _)).map(_.fileId).toSet -- accountsSetGrep
    val otherSetGrep = (allExs.map(_.fileId).toSet -- accountsSetGrep) -- accountSetGrep
    
    val cryptSetFancy = allExs.filter(ex => ex.fineAutoCat == "crypter" || ex.fineAutoCat == "crypting service").map(_.fileId).toSet
    val accountsSetFancy = allExs.filter(ex => ex.fineAutoCat.contains("account") && ex.semanticAutoHeadNoStem.endsWith("s")).map(_.fileId).toSet
    val accountSetFancy = allExs.filter(ex => ex.fineAutoCat.contains("account") && !ex.semanticAutoHeadNoStem.endsWith("s")).map(_.fileId).toSet
    
    val otherSetFancy = allExs.filter(ex => !ex.fineAutoCat.contains("account")).map(_.fileId).toSet
    
    
    val cryptSetGold = allExs.filter(_.semanticCatLabel.contains("CRYPT")).map(_.fileId).toSet
    val accountsSetGold = allExs.filter(ex => ex.semanticCatLabel.contains("ACCT_BULK") || ex.semanticCatLabel.contains("ACCT_HACKED")).map(_.fileId).toSet
    val accountSetGold = allExs.filter(ex => ex.semanticCatLabel.contains("ACCT_OGNAME") || ex.semanticCatLabel.contains("ACCT_STAT")).map(_.fileId).toSet
    val otherSetGold = allExs.filter(ex => !accountsSetGold.contains(ex.fileId) && !accountSetGold.contains(ex.fileId)).map(_.fileId).toSet
    
    val cryptServiceSetGrep = allExs.filter(ex => ex.words.flatten.map(_.toLowerCase.contains("crypt")).foldLeft(false)(_ || _) &&
                                                  ex.words.flatten.map(_.toLowerCase.contains("service")).foldLeft(false)(_ || _)).map(_.fileId).toSet
    val cryptServiceSetFancy = allExs.filter(ex => ex.fineAutoCat == "crypting service").map(_.fileId).toSet
    val cryptServiceSetGold = allExs.filter(ex => ex.semanticCatLabel.contains("SVC_CRYPT") || ex.semanticCatLabel.contains("SRVC_CRYPT")).map(_.fileId).toSet
    
    Logger.logss("CRYPT")
    symmDiff(cryptSetGrep, cryptSetGold)
    symmDiff(cryptSetFancy, cryptSetGold)
//    symmDiff(cryptSetGrep, cryptSetFancy)
    Logger.logss("CRYPT SERVICE")
    symmDiff(cryptServiceSetGrep, cryptServiceSetGold)
    symmDiff(cryptServiceSetFancy, cryptServiceSetGold)
    Logger.logss("ACCOUNTS")
    symmDiff(accountsSetGrep, accountsSetGold)
    symmDiff(accountsSetFancy, accountsSetGold)
//    symmDiff(accountsSetGrep, accountsSetFancy)
    Logger.logss("ACCOUNT")
    symmDiff(accountSetGrep, accountSetGold)
    symmDiff(accountSetFancy, accountSetGold)
//    symmDiff(accountSetGrep, accountSetFancy)
    
    Logger.logss("COMBINED")
    evaluateAccount(accountSetGrep, accountsSetGrep, accountSetGold, accountsSetGold)
    evaluateAccount(accountSetFancy, accountsSetFancy, accountSetGold, accountsSetGold)
    
    Logger.logss("ACCURACY")
    evaluateAccountAcc(accountSetGrep, accountsSetGrep, otherSetGrep, accountSetGold, accountsSetGold, otherSetGold)
    evaluateAccountAcc(accountSetFancy, accountsSetFancy, otherSetFancy, accountSetGold, accountsSetGold, otherSetGold)
  }
  
  def symmDiff(s1: Set[String], s2: Set[String]) {
    val overlap = (s1&s2).size
    Logger.logss("s1=" + s1.size + ", s2=" + s2.size + ", int=" + overlap)
    val prec = (overlap.toDouble/s1.size)
    val rec = (overlap.toDouble/s2.size) 
    Logger.logss("prec=" + prec + ", rec=" + rec + ", f1=" + (2 * prec * rec)/(prec + rec))
  }
  
  def evaluateAccount(predAccount: Set[String], predAccounts: Set[String], goldAccount: Set[String], goldAccounts: Set[String]) {
    val correct = (predAccount & goldAccount).size + (predAccounts & goldAccounts).size
    val prec = (correct.toDouble/(predAccount.size + predAccounts.size))
    val rec = (correct.toDouble/(goldAccount.size + goldAccounts.size)) 
    Logger.logss("prec=" + prec + ", rec=" + rec + ", f1=" + (2 * prec * rec)/(prec + rec))
  }
  
  def evaluateAccountAcc(predAccount: Set[String], predAccounts: Set[String], predOther: Set[String],
                         goldAccount: Set[String], goldAccounts: Set[String], goldOther: Set[String]) {
    val corrAcc = (predAccount & goldAccount).size
    val corrAccs = (predAccounts & goldAccounts).size
    val corrOther = (predOther & goldOther).size
    Logger.logss("account = " + (corrAcc.toDouble / goldAccount.size) +
                 "   accounts = " + (corrAccs.toDouble / goldAccounts.size) +
                 "   other = " + (corrOther.toDouble / goldOther.size) +
                 "   overall = " + (corrAcc + corrAccs + corrOther).toDouble / (goldAccount.size + goldAccounts.size + goldOther.size))
    
  }
}
