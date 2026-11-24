// Initialisation IDEMPOTENTE du replica set de production et du compte applicatif
// (lot 1 / 1a, décisions D1 et D15). Joué par le service « mongo-init » de docker-compose.yml,
// connecté en administrateur (root). Rejouable sans effet : chaque étape teste son état.
//
// Variables d'environnement lues (aucune valeur dans ce fichier) :
//   MONGO_APP_USERNAME, MONGO_APP_PASSWORD : compte de l'application ;
//   MONGO_APP_DATABASE : base applicative (défaut pointage_cleanic).

const appUser = process.env.MONGO_APP_USERNAME;
const appPassword = process.env.MONGO_APP_PASSWORD;
const appDb = process.env.MONGO_APP_DATABASE || 'pointage_cleanic';

if (!appUser || !appPassword) {
  print('MONGO_APP_USERNAME et MONGO_APP_PASSWORD sont obligatoires.');
  quit(1);
}

// 1. Replica set à un nœud. Le membre est déclaré sous le nom du service Docker « mongo » :
//    l'application, dans le même réseau Docker, le joint par ce nom.
let initie = true;
try {
  rs.status();
} catch (e) {
  if (e.codeName === 'NotYetInitialized' || /no replset config/i.test(String(e))) {
    initie = false;
  } else {
    throw e;
  }
}
if (!initie) {
  print('Replica set non initialisé : rs.initiate()');
  rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'mongo:27017' }] });
} else {
  print('Replica set déjà initialisé.');
}

// 2. Attente du PRIMARY (élection après rs.initiate).
for (let i = 0; i < 60 && !db.hello().isWritablePrimary; i++) {
  sleep(1000);
}
if (!db.hello().isWritablePrimary) {
  print('Le nœud n\'est pas devenu PRIMARY en 60 s.');
  quit(1);
}

// 3. Compte applicatif : lecture-écriture sur la seule base de l'application (pas root).
const admin = db.getSiblingDB('admin');
if (admin.getUser(appUser) === null) {
  print('Création du compte applicatif.');
  admin.createUser({ user: appUser, pwd: appPassword, roles: [{ role: 'readWrite', db: appDb }] });
} else {
  print('Compte applicatif déjà présent : mot de passe et rôles réalignés.');
  admin.updateUser(appUser, { pwd: appPassword, roles: [{ role: 'readWrite', db: appDb }] });
}
print('Initialisation terminée.');
