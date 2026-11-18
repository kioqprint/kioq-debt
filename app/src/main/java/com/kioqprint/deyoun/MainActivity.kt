package com.kioqprint.deyoun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Debt(val name: String, val original: Long, var remaining: Long, val due: String = "", val note: String = "")
data class Entry(val kind: String, val amount: Long, val label: String, val timestamp: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("deyoun", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seed = listOf(
            Debt("السكنة",192000,192000,"15 جانفي 2027"), Debt("ياسين",146000,146000,note="86,000 + 60,000"),
            Debt("رضا",150000,150000), Debt("شريفي",400000,400000), Debt("الحانوت",0,0,"23 أوت 2027","المبلغ غير محدد"),
            Debt("سفيان",30000,30000), Debt("أبي",15000,15000), Debt("العرصة",6000,6000), Debt("فتحي",10000,10000),
            Debt("معمر",5000,5000), Debt("مصطفى",130000,130000,note="100,000 + 30,000"), Debt("العيد",30000,30000),
            Debt("نصر الدين",10000,10000), Debt("أمينة",70000,70000), Debt("الحاج",80000,80000), Debt("كمال",1110,1110), Debt("فريد",1000,1000)
        )
        val debts = loadDebts(seed); val entries = loadEntries()
        setContent { MaterialTheme { DebtApp(debts,entries) { saveDebts(debts); saveEntries(entries) } } }
    }
    private fun loadDebts(seed: List<Debt>): MutableList<Debt> {
        val raw = prefs.getString("debts", null) ?: return seed.toMutableList()
        return try { val a=JSONArray(raw); MutableList(a.length()) { i -> val o=a.getJSONObject(i); Debt(o.getString("name"),o.getLong("original"),o.getLong("remaining"),o.optString("due"),o.optString("note")) } } catch (_:Exception) { seed.toMutableList() }
    }
    private fun saveDebts(ds:List<Debt>) { val a=JSONArray(); ds.forEach { d -> a.put(JSONObject().put("name",d.name).put("original",d.original).put("remaining",d.remaining).put("due",d.due).put("note",d.note)) }; prefs.edit().putString("debts",a.toString()).apply() }
    private fun loadEntries(): MutableList<Entry> {
        val raw = prefs.getString("entries", null) ?: return mutableListOf()
        return try { val a=JSONArray(raw); MutableList(a.length()) { i -> val o=a.getJSONObject(i); Entry(o.getString("kind"),o.getLong("amount"),o.getString("label"),o.getLong("timestamp")) } } catch (_:Exception) { mutableListOf() }
    }
    private fun saveEntries(es:List<Entry>) { val a=JSONArray(); es.forEach { e -> a.put(JSONObject().put("kind",e.kind).put("amount",e.amount).put("label",e.label).put("timestamp",e.timestamp)) }; prefs.edit().putString("entries",a.toString()).apply() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DebtApp(debts:MutableList<Debt>, entries:MutableList<Entry>, save:()->Unit) {
    var tab by remember { mutableStateOf("الرئيسية") }; var amount by remember { mutableStateOf("") }; var label by remember { mutableStateOf("") }; var dialog by remember { mutableStateOf("") }; var refresh by remember { mutableIntStateOf(0) }
    val total=debts.sumOf { it.remaining }; val income=entries.filter { it.kind=="دخل" }.sumOf { it.amount }; val expenses=entries.filter { it.kind=="مصروف" }.sumOf { it.amount }; val paid=entries.filter { it.kind=="سداد" }.sumOf { it.amount }; val available=income-expenses-paid
    val current=debts.firstOrNull { it.original>0 && it.remaining>0 }
    fun add(kind:String,n:Long,desc:String) { if(n>0) { entries.add(Entry(kind,n,desc.ifBlank { kind })); save(); refresh++ } }
    Scaffold(topBar={ TopAppBar(title={ Text("ديوني") }) }, bottomBar={ NavigationBar { listOf("الرئيسية","الديون","السجل").forEach { item -> NavigationBarItem(selected=tab==item,onClick={tab=item},label={Text(item)},icon={Text(if(item=="الرئيسية") "⌂" else if(item=="الديون") "د" else "≡")}) } } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(tab) {
                "الرئيسية" -> {
                    Card { Column(Modifier.padding(16.dp)) { Text("الديون المتبقية"); Text("${total.money()} دج",style=MaterialTheme.typography.headlineMedium); Text("المتاح للسداد: ${available.money()} دج") } }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Summary("المداخيل",income,Modifier.weight(1f)); Summary("المصاريف",expenses,Modifier.weight(1f)) }
                    current?.let { d -> Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { Text("الدين الحالي حسب ترتيبك"); Text(d.name,style=MaterialTheme.typography.headlineSmall); Text("المتبقي: ${d.remaining.money()} دج"); if(d.due.isNotBlank()) Text("موعد السداد: ${d.due}"); Button(onClick={dialog="سداد"},enabled=available>0) { Text("تسجيل دفعة") } } } } ?: Text("أكملت الديون المسجلة!")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={dialog="دخل"},modifier=Modifier.weight(1f)){Text("＋ دخل")}; OutlinedButton(onClick={dialog="مصروف"},modifier=Modifier.weight(1f)){Text("− مصروف")} }
                    Text("كل دخل إضافي يسجل فوراً ويزيد المبلغ المتاح للسداد.")
                }
                "الديون" -> LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { itemsIndexed(debts) { i,d -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text("${i+1}. ${d.name}",style=MaterialTheme.typography.titleMedium); Text("المتبقي: ${d.remaining.money()} دج"); if(d.due.isNotBlank()) Text("موعد: ${d.due}"); if(d.note.isNotBlank()) Text(d.note); if(d.original>0 && d.remaining==0L) Text("✓ مكتمل") } } } }
                else -> LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) { itemsIndexed(entries.reversed()) { _,e -> Card { Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) { Column { Text(e.label); Text(e.kind,style=MaterialTheme.typography.bodySmall) }; Text("${if(e.kind=="دخل") "+" else "−"}${e.amount.money()} دج") } } } }
            }
        }
    } }
    if(dialog.isNotBlank()) AlertDialog(onDismissRequest={dialog=""},title={Text(if(dialog=="سداد") "تسجيل سداد" else "تسجيل $dialog")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(amount,{amount=it},label={Text("المبلغ بالدينار")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number)); OutlinedTextField(label,{label=it},label={Text("المصدر أو الملاحظة")}); if(dialog=="سداد") Text("الدين الحالي: ${current?.name ?: "لا يوجد"}\nالمتاح: ${available.money()} دج") }},confirmButton={Button(onClick={ val n=amount.toLongOrNull() ?: 0L; if(dialog=="سداد" && current!=null && n>0 && available>0) { val pay=minOf(n,current.remaining,available); current.remaining-=pay; add("سداد",pay,"سداد ${current.name}") } else if(dialog!="سداد" && n>0) add(dialog,n,label); amount=""; label=""; dialog=""; save() }){Text("حفظ")}},dismissButton={TextButton(onClick={dialog=""}){Text("إلغاء")}})
}
@Composable fun Summary(title:String,value:Long,modifier:Modifier=Modifier) { Card(modifier) { Column(Modifier.padding(12.dp)) { Text(title); Text("${value.money()} دج",style=MaterialTheme.typography.titleMedium) } } }
fun Long.money():String="%,d".format(this).replace(',', ' ')
