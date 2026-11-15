/**
 * Echter Test von MorniSMP mit zwei Test-Spielern (mineflayer) auf Paper 26.1.2 – gestartet von test/run.sh.
 *   node smp-test.cjs <host> <port> <rcon-host:port> <rcon-passwort-datei> <server-ordner>
 *
 * Anna und Bert (offline-Konten) gehen durch: Marktstadt, Geld, Überweisung, Auktionshaus (verkaufen + kaufen mit
 * Gebühr), Auftrag (aufgeben, beliefern, abholen), Ankauf, Marktstand mieten, Zufalls-Teleport, Grundstück und
 * Schutz, Zuhause, Kopfgeld, Belohnung, Leiste.
 *
 * Verfasst mit Claude 🤖
 */
const fs = require('node:fs');
const mineflayer = require('mineflayer');
const { rcon } = require('./rcon.cjs');

const [HOST = '127.0.0.1', PORT = '25740', RCON = '127.0.0.1:25840', PASS_FILE] = process.argv.slice(2);
const PASS = fs.readFileSync(PASS_FILE, 'utf8').trim();
const VERSION = process.env.MC_VERSION ?? '26.1';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok) });
  console.log(`${ok ? '✓' : '✗'} ${name}${detail ? ` – ${detail}` : ''}`);
}

/** Text aus Chat-Komponenten (String, JSON oder NBT) zusammensetzen. */
function plain(v) {
  if (v == null) return '';
  if (typeof v === 'string') {
    if (v.startsWith('{') || v.startsWith('[')) {
      try {
        return plain(JSON.parse(v));
      } catch {
        return v;
      }
    }
    return v;
  }
  if (Array.isArray(v)) return v.map(plain).join('');
  if (typeof v !== 'object') return String(v);
  if (v.type === 'compound') return plain(v.value);
  if (v.type === 'list') return plain(v.value?.value ?? []);
  if (v.type === 'string') return v.value;
  let s = '';
  if ('text' in v) s += plain(v.text);
  if ('' in v) s += plain(v['']);
  if ('translate' in v) s += plain(v.translate);
  if ('extra' in v) s += plain(v.extra);
  return s;
}

const cmd = (...commands) => rcon(RCON, PASS, commands);

/** Steht der Block wirklich noch (Server fragen, nicht die Sicht des Test-Spielers)? */
async function serverHas(pos, name) {
  const out = await cmd(`execute if block ${pos.x} ${pos.y} ${pos.z} minecraft:${name}`);
  return /passed/i.test(out.join(' '));
}

async function waitFor(cond, ms = 5000, step = 100) {
  const start = Date.now();
  while (Date.now() - start < ms) {
    if (await cond()) return true;
    await sleep(step);
  }
  return Boolean(await cond());
}

let lastJoin = 0;
async function join(name) {
  const wait = lastJoin + 1500 - Date.now();
  if (wait > 0) await sleep(wait);
  lastJoin = Date.now();
  const bot = mineflayer.createBot({ host: HOST, port: Number(PORT), username: name, version: VERSION, auth: 'offline' });
  bot.chats = [];
  bot.bars = [];
  bot.titles = [];
  bot.on('messagestr', (m, position) => (position === 'game_info' ? bot.bars : bot.chats).push(m));
  bot._client.on('action_bar', (p) => bot.bars.push(plain(p.text)));
  bot._client.on('set_title_text', (p) => bot.titles.push(plain(p.text)));
  bot.on('kicked', (r) => console.log(`  (${name} gekickt: ${plain(r)})`));
  bot.on('error', (e) => console.log(`  (${name}: ${e.message})`));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    setTimeout(() => reject(new Error(`${name}: kein Spawn`)), 30000);
  });
  await sleep(2500);
  return bot;
}

const sees = (bot, re) => bot.chats.some((c) => re.test(c));
const last = (bot, re) => [...bot.chats].reverse().find((c) => re.test(c)) ?? '';
const clear = (...bots) => bots.forEach((b) => { b.chats.length = 0; b.bars.length = 0; });

/** Befehl schicken und auf eine passende Chat-Zeile warten. */
async function say(bot, text, re, ms = 6000) {
  bot.chats.length = 0;
  bot.chat(text);
  const ok = await waitFor(() => sees(bot, re), ms);
  return ok ? last(bot, re) : '';
}

/** „1.234,50 ₮“ → 1234.5 */
function taler(s) {
  const m = /([\d.]+(?:,\d{1,2})?) ₮/.exec(s);
  return m ? Number(m[1].replace(/\./g, '').replace(',', '.')) : NaN;
}

async function balance(bot) {
  return taler(await say(bot, '/geld', /Kontostand: [\d.,]+ ₮/));
}

function nextWindow(bot, ms = 5000) {
  return new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), ms);
    bot.once('windowOpen', (w) => {
      clearTimeout(t);
      resolve(w);
    });
  });
}

async function openBy(bot, text) {
  const w = nextWindow(bot);
  bot.chat(text);
  return w;
}

async function click(bot, slot, mode = 0) {
  await bot.clickWindow(slot, 0, mode);
  await sleep(400);
}

const count = (bot, name) => bot.inventory.items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);

async function hold(bot, name) {
  const it = bot.inventory.items().find((i) => i.name === name);
  if (!it) return false;
  await bot.equip(it, 'hand');
  await sleep(300);
  return true;
}

function title(w) {
  return plain(w?.title);
}

/** Warten, bis ein Teleport mit Wartezeit durch ist (Position springt). */
async function teleported(bot, from, ms = 12000) {
  return waitFor(() => bot.entity.position.distanceTo(from) > 8, ms, 200);
}

async function main() {
  await cmd('difficulty peaceful', 'time set day', 'gamerule doDaylightCycle false', 'weather clear');
  const anna = await join('Anna');
  const bert = await join('Bert');

  // ------------------------------------------------------------------ Marktstadt
  const pos = anna.entity.position.clone();
  check('Ankunft in der Marktstadt (bei 0/0)', Math.abs(pos.x) < 58 && Math.abs(pos.z) < 58, pos.toString());
  let solid = 0;
  const kinds = new Set();
  for (let dx = -20; dx <= 20; dx++) {
    for (let dz = -20; dz <= 20; dz++) {
      for (let dy = -1; dy <= 8; dy++) {
        const b = anna.blockAt(pos.offset(dx, dy, dz));
        if (b && b.name !== 'air') {
          solid++;
          kinds.add(b.name);
        }
      }
    }
  }
  check('Stadt steht (viele verschiedene Blöcke um die Ankunft)', solid > 1500 && kinds.size > 15,
    `${solid} Blöcke, ${kinds.size} Sorten`);
  const board = Object.values(anna.entities).find((e) => e.name === 'text_display');
  check('Bestenliste (schwebende Schrift) am Brunnen', Boolean(board));
  check('Begrüßung beim ersten Betreten', sees(anna, /Schön, dass du da bist/) || anna.titles.some((t) => /MorniSMP/.test(t)));
  const sidebar = Object.values(anna.scoreboards ?? {}).find((s) => /MorniSMP/.test(plain(s.title)));
  check('Leiste rechts mit MorniSMP', Boolean(sidebar));

  // ------------------------------------------------------------------ Geld
  const start = await balance(anna);
  check('/geld: Startguthaben 100 ₮', start === 100, String(start));
  await cmd('smpadmin geld geben Anna 2000', 'smpadmin geld geben Bert 2000');
  check('/smpadmin geld geben (Konsole)', (await balance(anna)) === 2100 && (await balance(bert)) === 2100);
  clear(bert);
  const paid = await say(anna, '/zahlen Bert 50', /überwiesen/);
  check('/zahlen Bert 50', /50 ₮ an Bert überwiesen/.test(paid), paid);
  await waitFor(() => sees(bert, /Anna hat dir 50 ₮ überwiesen/), 3000);
  check('Bert bekommt Bescheid', sees(bert, /Anna hat dir 50 ₮ überwiesen/));
  check('Kontostände nach /zahlen: 2050 / 2150', (await balance(anna)) === 2050 && (await balance(bert)) === 2150);
  const broke = await say(anna, '/zahlen Bert 999999', /reicht dein Geld nicht|Wirklich/);
  check('Zu wenig Geld wird abgelehnt (oder erst nachgefragt)', broke !== '' || anna.currentWindow != null);
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  const top = await say(anna, '/reichste', /Die Reichsten/);
  check('/reichste', top !== '');

  // ------------------------------------------------------------------ Auktionshaus
  await cmd('clear Anna', 'clear Bert', 'give Anna diamond 16');
  await sleep(800);
  await hold(anna, 'diamond');
  const listed = await say(anna, '/ah verkaufen 200', /Angeboten|Angebote/);
  check('/ah verkaufen 200 (16 Diamanten aus der Hand)', /Angeboten: 16× .*200 ₮/.test(listed), listed);
  check('Diamanten sind aus dem Inventar weg', count(anna, 'diamond') === 0);
  const ah = await openBy(bert, '/ah');
  check('Bert öffnet das Auktionshaus', /Auktionshaus/.test(title(ah)), title(ah));
  const listing = ah?.slots[9];
  check('Angebot liegt im ersten Platz (Diamant)', listing?.name === 'diamond' && listing.count === 16, listing?.name);
  let confirm = nextWindow(bert);
  await click(bert, 9);
  const cw = await confirm;
  check('Kauf-Nachfrage öffnet sich', /Wirklich kaufen/.test(title(cw)), title(cw));
  clear(anna, bert);
  await click(bert, 10);
  await waitFor(() => sees(bert, /Gekauft/), 4000);
  check('Bert hat gekauft (Nachricht)', sees(bert, /Gekauft: 16× .*200 ₮ von Anna/), last(bert, /Gekauft/));
  await waitFor(() => count(bert, 'diamond') === 16, 3000);
  check('Bert hat 16 Diamanten im Inventar', count(bert, 'diamond') === 16, String(count(bert, 'diamond')));
  await waitFor(() => sees(anna, /Verkauft!/), 3000);
  check('Anna bekommt „Verkauft!“ mit Gebühr', sees(anna, /Verkauft!.*Bert.*200 ₮.*Gebühr 4 ₮/), last(anna, /Verkauft/));
  check('Geld: Anna +196 (2 % Gebühr), Bert −200', (await balance(anna)) === 2246 && (await balance(bert)) === 1950);

  // ------------------------------------------------------------------ Aufträge
  await cmd('give Bert wheat 1', 'give Anna wheat 10');
  await sleep(800);
  await hold(bert, 'wheat');
  confirm = nextWindow(bert);
  bert.chat('/auftraege neu 10 5');
  const ow = await confirm;
  check('Auftrag: Nachfrage mit Summe', /Auftrag aufgeben/.test(title(ow)), title(ow));
  clear(bert);
  await click(bert, 10);
  await waitFor(() => sees(bert, /Auftrag steht/), 4000);
  check('Auftrag steht (10 Weizen zu 5 ₮)', sees(bert, /Auftrag steht: 10× .*5 ₮/), last(bert, /Auftrag/));
  check('50 ₮ liegen in der Treuhand (Bert 1900)', (await balance(bert)) === 1900);
  const board2 = await openBy(anna, '/auftraege');
  check('Anna öffnet das Auftragsbrett', /Auftragsbrett/.test(title(board2)), title(board2));
  check('Auftrag liegt im ersten Platz (Weizen)', board2?.slots[9]?.name === 'wheat', board2?.slots[9]?.name);
  const dw = nextWindow(anna);
  await click(anna, 9);
  const deliver = await dw;
  check('Liefern-Menü', /Liefern/.test(title(deliver)), title(deliver));
  clear(anna, bert);
  await click(anna, 11);
  await waitFor(() => sees(anna, /geliefert/), 4000);
  check('Anna liefert 10 Weizen (+49 ₮, 1 ₮ Gebühr)', sees(anna, /10× .*an Bert geliefert: \+49 ₮/), last(anna, /geliefert/));
  check('Weizen ist bei Anna weg', count(anna, 'wheat') === 0);
  check('Anna hat jetzt 2295 ₮', (await balance(anna)) === 2295);
  await waitFor(() => sees(bert, /Auftrag erfüllt/), 3000);
  check('Bert: „Auftrag erfüllt!“', sees(bert, /Auftrag erfüllt/), last(bert, /Auftrag/));
  const mail = await openBy(bert, '/abholen');
  check('Abholfach öffnet sich', /Abholfach/.test(title(mail)), title(mail));
  clear(bert);
  const before = count(bert, 'wheat');
  await click(bert, 49);
  await waitFor(() => count(bert, 'wheat') >= before + 10, 3000);
  check('Bert holt die 10 Weizen ab', count(bert, 'wheat') === before + 10, `${before} → ${count(bert, 'wheat')}`);
  if (bert.currentWindow) bert.closeWindow(bert.currentWindow);

  // ------------------------------------------------------------------ Ankauf
  await cmd('give Anna wheat 64', 'give Anna diamond 1');
  await sleep(800);
  const bw = await openBy(anna, '/verkaufen');
  check('Ankauf öffnet sich', /Ankauf/.test(title(bw)), title(bw));
  for (const name of ['wheat', 'diamond']) {
    const it = bw?.findInventoryItem(name, null, false);
    if (it) await click(anna, it.slot, 1);
  }
  await sleep(600);
  const btn = plain(anna.currentWindow?.slots[49]?.customName);
  check('Knopf zeigt den Erlös (64 Weizen = 32 ₮)', /32 ₮/.test(btn), btn);
  clear(anna);
  await click(anna, 49);
  await waitFor(() => sees(anna, /an die Stadt verkauft/), 3000);
  check('Ankauf: +32 ₮ (Weizen)', sees(anna, /an die Stadt verkauft: \+32 ₮/), last(anna, /verkauft/));
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  await sleep(800);
  check('Diamant (kauft die Stadt nicht) kommt zurück', count(anna, 'diamond') === 1, String(count(anna, 'diamond')));
  check('Anna hat 2327 ₮', (await balance(anna)) === 2327);
  await cmd('give Anna wheat 300');
  await sleep(800);
  const bw2 = await openBy(anna, '/verkaufen');
  for (const it of bw2 ? bw2.items().filter((i) => i.name === 'wheat' && i.slot >= bw2.inventoryStart) : []) {
    await bot_shift(anna, it.slot);
  }
  await sleep(600);
  const btn2 = plain(anna.currentWindow?.slots[49]?.customName);
  const value = taler(btn2);
  // heute schon 64: noch 192 × 0,50 = 96, danach 108 × 0,25 (50 %) = 27 → 123 statt 150
  check('Sinkende Preise: 300 Weizen bringen 123 ₮ statt 150 ₮', value === 123, btn2);
  await click(anna, 49);
  if (anna.currentWindow) anna.closeWindow(anna.currentWindow);
  await sleep(500);

  // ------------------------------------------------------------------ Marktstand (über das Schild am Stand)
  const signText = (b) => plain(b?.blockEntity?.front_text?.messages?.[0]);
  const signPos = anna.findBlocks({ matching: (b) => /hanging_sign/.test(b.name), maxDistance: 40, count: 40 })
    .find((p) => /^Stand \d+$/.test(signText(anna.blockAt(p))));
  const sign = signPos ? anna.blockAt(signPos) : null;
  check('Hängeschild eines Marktstands gefunden', Boolean(sign), signText(sign));
  if (sign) {
    await cmd(`tp Anna ${sign.position.x + 0.5} ${sign.position.y - 2} ${sign.position.z + 0.5}`);
    await sleep(1200);
    const sw = nextWindow(anna);
    await anna.activateBlock(anna.blockAt(sign.position));
    const stall = await sw;
    check('Klick aufs Standschild öffnet den Stand', /Marktstand \d+/.test(title(stall)), title(stall));
    if (stall) {
      clear(anna);
      await click(anna, 13);
      await waitFor(() => sees(anna, /gehört dir/), 3000);
      check('Stand gemietet (300 ₮ pro Woche)', sees(anna, /Stand \d+ gehört dir/), last(anna, /Stand/));
    }
  }
  const afterStall = await balance(anna);

  // ------------------------------------------------------------------ Kopfgeld + Belohnung
  clear(bert);
  const bounty = await say(anna, '/kopfgeld Bert 100', /Kopfgeld auf Bert/);
  check('/kopfgeld Bert 100 (alle sehen es)', bounty !== '' && (await waitFor(() => sees(bert, /Kopfgeld auf Bert/), 2000)));
  const reward = await say(bert, '/belohnung', /Belohnung/);
  check('/belohnung: 25 ₮ am ersten Tag', /Tägliche Belohnung: 25 ₮/.test(reward), reward);
  const again = await say(bert, '/belohnung', /schon/);
  check('Belohnung nur einmal am Tag', again !== '');

  // ------------------------------------------------------------------ Schutz in der Stadt
  await cmd('gamemode creative Bert');
  await sleep(500);
  const tpos = bert.entity.position.floored();
  let target = null;
  for (const [dx, dz] of [[1, 0], [0, 1], [-1, 0], [0, -1], [2, 0]]) {
    const b = bert.blockAt(tpos.offset(dx, -1, dz));
    if (b && b.name !== 'air' && b.diggable) {
      target = b;
      break;
    }
  }
  if (target) {
    const name = target.name;
    try {
      await bert.dig(target, true);
    } catch {
      // abgebrochen ist auch gut
    }
    await sleep(1000);
    check('Stadt ist geschützt (Block bleibt, laut Server)', await serverHas(target.position, name), name);
  } else {
    check('Stadt ist geschützt (Block bleibt)', false, 'kein Block gefunden');
  }
  await cmd('gamemode survival Bert');

  // ------------------------------------------------------------------ Zufalls-Teleport
  const p0 = anna.entity.position.clone();
  clear(anna);
  anna.chat('/rtp nah');
  const moved = await teleported(anna, p0, 30000);
  await waitFor(() => sees(anna, /Du bist bei/), 3000);
  const p1 = anna.entity.position.clone();
  const dist = Math.hypot(p1.x, p1.z);
  check('/rtp nah: Suche + 3 s Wartezeit + Teleport', moved && sees(anna, /sicheren Platz/), p1.toString());
  check('Ziel liegt im Ring 500–2000', dist >= 480 && dist <= 2020, `Abstand ${dist.toFixed(0)}`);
  await waitFor(() => anna.blockAt(p1.offset(0, -0.5, 0)) != null, 10000);
  const ground = anna.blockAt(p1.offset(0, -0.5, 0));
  const feet = anna.blockAt(p1);
  check('Sicherer Platz: fester Boden, Füße frei, kein Wasser', ground && ground.boundingBox === 'block' &&
    !/water|lava/.test(ground.name) && feet && feet.boundingBox === 'empty', `${ground?.name} / ${feet?.name}`);
  check('Abklingzeit danach', (await say(anna, '/rtp nah', /warten|wieder möglich/)) !== '');

  // ------------------------------------------------------------------ Grundstück
  const claimed = await say(anna, '/gs sichern', /Gesichert|gehört|nah an der Marktstadt/);
  check('/gs sichern (gratis)', /Gesichert! Das ist dein Grundstück Nr\. 1/.test(claimed), claimed);
  await cmd('tp Bert Anna', 'gamemode creative Bert');
  await sleep(2500);
  const bp = bert.entity.position.floored();
  const victim = bert.blockAt(bp.offset(1, -1, 0)) ?? bert.blockAt(bp.offset(0, -1, 0));
  if (victim && victim.name !== 'air') {
    const name = victim.name;
    clear(bert);
    try {
      await bert.dig(victim, true);
    } catch {
      // abgebrochen
    }
    await sleep(1000);
    check('Grundstück geschützt: Bert kann nicht abbauen (laut Server)', await serverHas(victim.position, name), name);
    check('Hinweis „gehört Anna“', bert.bars.some((b) => /gehört Anna/.test(b)), bert.bars.join(' | '));
    await say(anna, '/gs vertrauen Bert', /darf jetzt/);
    await sleep(300);
    // ein anderer Block im selben Chunk (die Sicht des Test-Spielers auf den ersten ist evtl. noch „Luft“)
    const chunk = (v) => `${Math.floor(v.x / 16)}:${Math.floor(v.z / 16)}`;
    let second = null;
    for (const [dx, dz] of [[-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]]) {
      const b = bert.blockAt(bp.offset(dx, -1, dz));
      if (b && b.name !== 'air' && !/water|lava/.test(b.name) && b.diggable && chunk(b.position) === chunk(victim.position)) {
        second = b;
        break;
      }
    }
    if (second) {
      try {
        await bert.dig(second, true);
      } catch {
        // abgebrochen
      }
      await sleep(1000);
    }
    check('Nach /gs vertrauen darf Bert bauen (laut Server weg)', second != null && await serverHas(second.position, 'air'),
      second?.name);
  } else {
    check('Grundstück geschützt: Bert kann nicht abbauen', false, 'kein Block');
  }
  await cmd('gamemode survival Bert');
  const info = await say(anna, '/gs', /gehört Anna|gehört/);
  check('/gs info zeigt den Besitzer', /gehört Anna/.test(info), info);

  // ------------------------------------------------------------------ Ladenkiste auf dem eigenen Grundstück
  {
    const ap = anna.entity.position.floored();
    const chunkOf = (x, z) => `${Math.floor(x / 16)}:${Math.floor(z / 16)}`;
    let spot = null;
    for (const [dx, dz] of [[2, 0], [-2, 0], [0, 2], [0, -2], [2, 2], [-2, -2]]) {
      if (chunkOf(ap.x + dx, ap.z + dz) === chunkOf(ap.x, ap.z)) {
        spot = ap.offset(dx, 0, dz);
        break;
      }
    }
    await cmd(`setblock ${spot.x} ${spot.y} ${spot.z} chest`, `setblock ${spot.x} ${spot.y + 1} ${spot.z} oak_sign`,
      'clear Anna', 'give Anna cobblestone 32');
    await sleep(1000);
    await hold(anna, 'cobblestone');
    const signBlock = anna.blockAt(spot.offset(0, 1, 0));
    await anna.lookAt(signBlock.position.offset(0.5, 0.5, 0.5), true);
    await anna.activateBlock(signBlock);
    await sleep(700);
    const setup = nextWindow(anna);
    anna.updateSign(anna.blockAt(spot.offset(0, 1, 0)), '[Laden]');
    const sw = await setup;
    check('[Laden]-Schild öffnet „Laden einrichten“', /Laden einrichten/.test(title(sw)), title(sw));
    clear(anna);
    if (sw) await click(anna, 16);
    await waitFor(() => sees(anna, /Laden für .* ist offen/), 3000);
    check('Laden ist offen', sees(anna, /Laden für .* ist offen/), last(anna, /Laden/));
    await cmd(`item replace block ${spot.x} ${spot.y} ${spot.z} container.0 with cobblestone 64`, 'tp Bert Anna');
    await sleep(2000);
    const annaBefore = await balance(anna);
    const bertBefore = await balance(bert);
    const cw2 = nextWindow(bert);
    await bert.lookAt(spot.offset(0.5, 0.5, 0.5), true);
    await bert.activateBlock(bert.blockAt(spot));
    const shopWin = await cw2;
    check('Kunde sieht den Laden statt der Kiste', /Laden von Anna/.test(title(shopWin)), title(shopWin));
    clear(anna, bert);
    if (shopWin) await click(bert, 13);
    await waitFor(() => sees(bert, /Gekauft: 32×/), 3000);
    check('Bert kauft 32 Steine aus dem Laden', sees(bert, /Gekauft: 32× .* bei Anna/) && count(bert, 'cobblestone') === 32,
      last(bert, /Gekauft/));
    if (bert.currentWindow) bert.closeWindow(bert.currentWindow);
    const price = bertBefore - (await balance(bert));
    check('Geld ging von Bert an Anna (keine Laden-Gebühr)', price > 0 && (await balance(anna)) === annaBefore + price,
      `${price} ₮`);
    check('Ladenkiste selbst kann Bert nicht abbauen', await (async () => {
      await cmd('gamemode creative Bert');
      try {
        await bert.dig(bert.blockAt(spot), true);
      } catch {
        // abgebrochen
      }
      await sleep(800);
      await cmd('gamemode survival Bert');
      return serverHas(spot, 'chest');
    })());
  }

  // ------------------------------------------------------------------ Zuhause
  const base = anna.entity.position.clone();
  check('/sethome basis', (await say(anna, '/sethome basis', /gesetzt/)) !== '');
  anna.chat('/spawn');
  const toTown = await teleported(anna, base, 15000);
  check('/spawn bringt in die Stadt', toTown && Math.hypot(anna.entity.position.x, anna.entity.position.z) < 60,
    anna.entity.position.toString());
  await sleep(1500);
  const inTown = anna.entity.position.clone();
  anna.chat('/home basis');
  const home = await teleported(anna, inTown, 15000);
  check('/home basis bringt zurück', home && anna.entity.position.distanceTo(base) < 2, anna.entity.position.toString());
  await sleep(1000);
  const homes = await say(anna, '/homes', /Deine Zuhause/);
  check('/homes listet das Zuhause', homes !== '');

  // ------------------------------------------------------------------ Kampf in der Wildnis + Schutz in der Stadt
  await cmd('tp Bert Anna');
  await sleep(2000);
  // Annas Grundstück ist friedlich: kein Schaden
  const hp0 = anna.health;
  const annaEntity = bert.players.Anna?.entity;
  if (annaEntity) {
    await bert.lookAt(annaEntity.position.offset(0, 1.4, 0), true);
    bert.attack(annaEntity);
    await sleep(800);
  }
  check('Kein Schaden auf friedlichem Grundstück', anna.health === hp0, `${hp0} → ${anna.health}`);

  // ------------------------------------------------------------------ Statistik/Admin
  const adm = await cmd('smpadmin info');
  check('/smpadmin info (Konsole)', /Geld bei Spielern/.test(adm.join(' ')), adm.join(' ').slice(0, 160));
  void afterStall;

  anna.quit();
  bert.quit();
  await sleep(1000);
  const ok = results.filter((r) => r.ok).length;
  console.log(`\n${ok}/${results.length} Prüfungen bestanden`);
  process.exit(ok === results.length ? 0 : 1);
}

async function bot_shift(bot, slot) {
  await bot.clickWindow(slot, 0, 1);
  await sleep(150);
}

main().catch((e) => {
  console.error(e);
  const ok = results.filter((r) => r.ok).length;
  console.log(`\nABBRUCH nach ${ok}/${results.length}`);
  process.exit(1);
});
