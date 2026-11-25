// Détection des doublons et des index existants — LECTURE SEULE (lot 1 / 1a, décision D3).
//
// À jouer sur la base de production AVANT de démarrer la version 1a : les ChangeUnits d'index
// (0101 à 0105) refusent de créer un index unique en présence de doublons, et l'application
// refuse alors de démarrer. Ce script dit à l'avance ce qui bloquerait.
//
//   mongosh "<URI>" --quiet --file ops/mongo/detecter-doublons.js
//
// N'écrit rien. Affiche, pour chaque index unique de IndexesAttendus, le nombre de valeurs en
// double et un échantillon d'identifiants ; puis la liste des index déjà présents en base (pour
// repérer un index de même nom ou de mêmes clés mais d'une autre définition).
//
// ⚠ Cette liste doit rester alignée sur IndexesAttendus.java (vérifié par DetecterDoublonsScriptTest).

const UNIQUES = [
  // collection, nom, clés, filtre partiel (null = aucun)
  ['employes_complet', 'agentId_unique', { agentId: 1 }, { agentId: { $type: 'string' } }],
  ['employes_complet', 'matricule_unique', { matricule: 1 }, { matricule: { $type: 'string' } }],
  ['categories_professionnelles', 'code_unique', { code: 1 }, { code: { $type: 'string' } }],
  ['dossiers_employes', 'matricule_unique', { matricule: 1 }, { matricule: { $type: 'string' } }],
  ['dossiers_employes', 'agentId_unique', { agentId: 1 }, { agentId: { $type: 'string' } }],
  ['rh_jours_feries', 'date_unique', { date: 1 }, null],
  ['participations_formation', 'session_employe_idx', { sessionId: 1, employeId: 1 }, null],
  ['stockv2_bons_entree', 'reference_unique', { reference: 1 }, { reference: { $type: 'string' } }],
  ['stockv2_bons_sortie', 'reference_unique', { reference: 1 }, { reference: { $type: 'string' } }],
  ['stockv2_chantiers', 'reference_unique', { reference: 1 }, { reference: { $type: 'string' } }],
  ['stockv2_inventaires', 'reference_unique', { reference: 1 }, { reference: { $type: 'string' } }],
  ['stockv2_mouvements', 'reference_unique', { reference: 1 }, { reference: { $type: 'string' } }],
  ['stockv2_produits', 'code_unique', { code: 1 }, { code: { $type: 'string' } }],
  ['stockv2_etats_stock', 'produit_site_unique', { produitId: 1, siteId: 1 }, null],
  ['terrain_materiel', 'code_unique', { code: 1 }, { code: { $type: 'string' } }],
  ['produits_phyto', 'numeroHomologation_unique', { numeroHomologation: 1 }, { numeroHomologation: { $type: 'string' } }],
  ['sites_clients', 'code_unique', { code: 1 }, { code: { $type: 'string' } }],
  ['production_chimie_matieres_premieres', 'code_unique', { code: 1 }, null],
  ['production_chimie_formulations', 'code_unique', { code: 1 }, null],
  ['production_chimie_ordres_fabrication', 'numero_unique', { numero: 1 }, null],
  ['production_chimie_lots', 'numero_unique', { numero: 1 }, null],
  ['production_chimie_formats_conditionnement', 'code_unique', { code: 1 }, null],
  ['evenements_consommes', 'consommateur_evenement_unique', { consommateur: 1, evenementId: 1 }, null],
  ['registre_pieces', 'type_piece_unique', { type: 1, pieceId: 1 }, null],
  ['registre_pieces', 'type_numero_unique', { type: 1, numero: 1 }, { numero: { $type: 'string' } }],
];

let bloquants = 0;
print('=== Index uniques : valeurs en double ===');
for (const [collection, nom, cles, filtre] of UNIQUES) {
  if (!db.getCollectionNames().includes(collection)) {
    print(`  ${collection}.${nom} : collection absente (rien à vérifier)`);
    continue;
  }
  const groupe = {};
  // Champ absent et champ null sont la même valeur pour un index unique : $ifNull les regroupe.
  for (const champ of Object.keys(cles)) groupe[champ.replace(/\./g, '_')] = { $ifNull: ['$' + champ, null] };
  const pipeline = [];
  if (filtre) pipeline.push({ $match: filtre });
  pipeline.push(
    { $group: { _id: groupe, nombre: { $sum: 1 }, ids: { $push: '$_id' } } },
    { $match: { nombre: { $gt: 1 } } },
  );
  const doublons = db.getCollection(collection).aggregate(pipeline, { allowDiskUse: true }).toArray();
  if (doublons.length === 0) {
    print(`  ${collection}.${nom} : OK`);
  } else {
    bloquants++;
    print(`  ${collection}.${nom} : ${doublons.length} valeur(s) en double — BLOQUANT`);
    doublons.slice(0, 20).forEach(d =>
      print(`      ${JSON.stringify(d._id)} × ${d.nombre} : ${d.ids.slice(0, 10).map(String).join(', ')}`));
  }
}

print('\n=== Index déjà présents en base (hors _id_) ===');
for (const collection of db.getCollectionNames().sort()) {
  const index = db.getCollection(collection).getIndexes().filter(i => i.name !== '_id_');
  if (index.length === 0) continue;
  print(`  ${collection}`);
  index.forEach(i => print(`      ${i.name} ${JSON.stringify(i.key)}${i.unique ? ' unique' : ''}`
    + `${i.partialFilterExpression ? ' partiel ' + JSON.stringify(i.partialFilterExpression) : ''}`
    + `${i.sparse ? ' sparse' : ''}`));
}

print(`\n${bloquants === 0 ? 'Aucun doublon : les index uniques peuvent être créés.'
  : bloquants + ' index unique(s) bloqué(s) : à trancher avant la mise en service (voir docs/refonte/1a-socle-technique.md).'}`);
