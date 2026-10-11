# Gabay sa Mga Komento ng Panel (personal na gabay)

Capstone: **A Mobile-Based Recruitment System Using Machine Learning For Mr. D.I.Y.**
Our Lady of Fatima University, College of Computer Studies, Antipolo City. Petsa ng komento: **25/05/2026**.
Mga proponent: Antuerpia Ray Miguel R., Conde Sprewell B., Balasta Joshua Allen P., Sultan Salahoden E.

Kopya nito ay nasa `MrDIY/PANEL_COMMENTS_GUIDE.md` at `mrdiyreact-admin/mrdiy-admin/PANEL_COMMENTS_GUIDE.md`. Ito ang listahan na dapat nating balikan bago ang bawat bagong feature at bago ang defense. Ang parehong talaan ay nasa seksyon 0 ng `ADMIN_MAJOR_PLAN.md`.

**Huling update ng kalagayan:** 2026-10-12

---

## Ang talaan

| # | Panelist | Komento ng panel | Kalagayan |
|---|---|---|---|
| 1 | Engr. Eliseo Francisco, MIT | Notification o status update pagkatapos mag-apply | **Tapos na** (app, tray, email). Push kapag nakasara ang app ay kailangan pa ng Firebase key. |
| 2 | Engr. Francisco | Mga hakbang mula pag-apply hanggang makapagtrabaho | **Kulang:** timeline, interview, checklist ng dokumento |
| 3 | Engr. Francisco | Gaano kabilis magsisimula ang applicant | **Kulang:** Hiring Velocity |
| 4 | Engr. Francisco | Web-based (optional) | **Tapos na:** ang admin web |
| 5 | Engr. Francisco | Formatted biodata ng client (kung available) | **Kulang**, at wala pa sa anumang plano namin noon |
| 6 | Michael Angelo Manalo, MIT | Improvements ng machine learning | **Bahagi pa lang** (lapit ng lokasyon, mga tumugmang salita) |
| 7 | Manalo | Wastong ranking system na may strengths at weaknesses | **Kulang** |
| 8 | Manalo | Automated AI explanation ng ranking | **Bahagi pa lang** (walang pangungusap na paliwanag) |

---

## Bawat komento: ano ang meron, ano ang kulang, paano ipapakita

### 1. Notification o status update pagkatapos mag-apply
- [x] "Application received" kapag nag-apply (awtomatiko mula sa database)
- [x] Bell sa app na may pulang tuldok, listahan ng notification na may tamang oras
- [x] Notification sa tray ng phone (lumalabas kapag binuksan ang app at bawat ~15 minuto)
- [x] Email sa pagpalit ng status (Under review, Accepted, Rejected) na may logo at kulay ng MR.DIY
- [ ] Push na lumalabas agad kahit nakasara ang app: **kailangan ang Firebase service account key**
- **Demo:** mag-apply, tapos palitan ang status sa admin; ipakita ang bell, ang tray at ang email.
- **Sagot kung tatanungin:** "Tatlong paraan: sa loob ng app, sa tray ng phone, at sa email. Naka-save per user ang bawat notification."

### 2. Mga hakbang mula pag-apply hanggang makapagtrabaho
- [x] Status na Pending, Under review, Accepted, Rejected
- [ ] **Timeline** sa app: Submitted, Under Review, Shortlisted, Interview, Hired
- [ ] **Interview** (iskedyul, kumpirmasyon ng applicant)
- [ ] **Checklist ng dokumento** pagkatapos ma-hire (SSS, PhilHealth, Pag-IBIG, NBI, medical, TIN, contract)
- **Plano:** `ADMIN_MAJOR_PLAN.md` (seksyon 2C, 2D, 2I) at `CAPSTONE_ADVANCED_AI_PLANNING.md` (Section 1)

### 3. Gaano kabilis magsisimula ang applicant
- [ ] **Hiring Velocity Estimator**: ipakita sa applicant at sa HR ang "Estimated start: 2 - 4 business days"
- Pormula sa plano: 7 araw na base, -2 kung match 85% pataas, -1 kung kumpleto ang profile at resume, -1 kung kumpleto ang dokumento, +3 kung kulang sa pangunahing skill.
- **Plano:** `CAPSTONE_ADVANCED_AI_PLANNING.md` (Section 2.1)

### 4. Web-based (optional)
- [x] Admin web (`mrdiy-admin`): Dashboard, Jobs, Applications, Messages, may email at notification
- **Sagot:** "Dinagdag namin ang web para sa HR, na nakakabit sa iisang database ng app."

### 5. Formatted biodata ng client
- [ ] **Biodata na awtomatikong nabubuo** mula sa profile ng applicant (pangalan, contact, address, edukasyon, karanasan, skills, larawan)
- [ ] **I-download bilang PDF** ng HR para ma-print
- [ ] Mga field na hinihingi ng MR.DIY (tatanungin natin kung mayroon silang sariling format)
- **Plano:** `ADMIN_MAJOR_PLAN.md` (seksyon 2K)
- **Tanong para sa MR.DIY:** may sarili ba silang form ng biodata na dapat sundin?

### 6. Improvements ng machine learning
- [x] TF-IDF at Cosine Similarity (70% ng score)
- [x] **Lapit ng lokasyon** (30% ng score)
- [x] Paliwanag ng mga salitang tumugma, na nakahighlight sa resume (admin)
- [x] Mas mabilis na pagkuwenta (inayos ang mabagal na bahagi)
- [ ] Mas magandang pagtutugma: mga kasingkahulugan at Tagalog na termino, bigat ng bawat skill, taon ng karanasan at antas
- [ ] **Pagsukat ng kalidad**: ihambing ang ranking sa pasya ng HR (halimbawa kung ilan sa Accepted ang nasa itaas ng ranking)
- **Sagot kung tatanungin:** "Pinili namin ang TF-IDF dahil maipapaliwanag nito kung bakit, at dinagdagan namin ng lokasyon. Susunod ang mas magandang pagtutugma ng skills."

### 7. Ranking system na may strengths at weaknesses
- [x] Match % ng bawat applicant sa bawat job
- [ ] **Ranking ng mga applicant bawat job** (#1, #2, #3) sa admin, naka-sort ayon sa match
- [ ] Para sa bawat applicant: **mga lakas** (tumugmang skills, karanasan, malapit na lugar) at **mga kulang** (hindi nakalistang kinakailangan)
- **Plano:** `ADMIN_MAJOR_PLAN.md` (seksyon 2L) at `CAPSTONE_ADVANCED_AI_PLANNING.md` (Section 2.2)

### 8. Automated AI explanation ng ranking
- [x] Mga dahilan sa app: "Branch is about 4 km from you", mga tumugmang skill
- [x] Listahan ng mga salitang nag-ambag at porsyento ng bawat isa (admin)
- [ ] **Isang pangungusap na paliwanag** na awtomatikong nabubuo, halimbawa: "Si [Pangalan] ay #2 na may 68% na match sa Cashier. Ang ranggo ay dahil sa 3 sa 5 kinakailangang skill (POS, cash handling, customer service) at 3 taong karanasan. Kulang siya sa: …"
- **Plano:** `CAPSTONE_ADVANCED_AI_PLANNING.md` (Section 3.3)

---

## Mga paalala bago ang defense
- Ang `CAPSTONE_DEFENSE_GUIDE.md` ay may mga sagot na naglalarawan ng mga feature na **wala pa** (Shortlisted at Hired, pre-employment checklist, Hiring Velocity, "Generate AI Description"). Dapat itong itayo o itama ang sagot, para totoo ang lahat ng ipapakita.
- Ang "30 unit tests" sa gabay ay **38** na ngayon.
- Para sa bawat feature na itatayo, isulat dito kung aling komento ang sinasagot, at i-tick ang checkbox.
- Ang **AI na huli** (mga pagpapahusay ng ML, ranking at paliwanag, mga komento 6 hanggang 8) ay gagawin pagkatapos ng pipeline, interview at checklist, para may datos na ma-rank at maipaliwanag.

## Pagkakasunod-sunod na mungkahi
1. Komento 2: pipeline, timeline sa app, interview, checklist
2. Komento 3: Hiring Velocity
3. Komento 5: formatted biodata (PDF)
4. Komentos 7, 8, 6: ranking na may strengths at weaknesses, paliwanag na pangungusap, at mga pagpapahusay ng ML
5. Komento 1: i-activate ang push (Firebase key)
