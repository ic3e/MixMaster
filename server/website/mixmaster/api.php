<?php
/*
 * MixMaster company server: the website version.
 *
 * Upload the whole "mixmaster" folder to the company's website, so this file opens at something
 * like https://conwic.fi/mixmaster/api.php. There is nothing to fill in: the first time anything
 * talks to it, it makes itself a database file in data/ under a random name nobody can guess.
 * The employer's app then claims it (Settings -> Company -> Set up), and from then on only a phone
 * holding an access code the employer made can read or write anything.
 *
 * Needs PHP 7.0 or newer with SQLite (pdo_sqlite), which nearly every web host has switched on.
 * A host without SQLite can use MySQL/MariaDB instead: see config.sample.php.
 *
 * The Google version (server/google/Code.gs) speaks exactly the same language; the app does not
 * care which one it is talking to.
 */

define('MM_PROTOCOL', 1);
define('MM_PAGE', 500);
define('MM_MAX_BODY', 8 * 1024 * 1024);
define('MM_MAX_PUSH', 1000);
define('MM_CODE_ALPHABET', '23456789ABCDEFGHJKLMNPQRSTUVWXYZ');
define('MM_JOIN_TRIES_PER_HOUR', 30);

/**
 * Who may change what. A table the app adds later that is not on this list is the owner's to
 * change until the server is updated, and everybody can still read it.
 */
function mm_group($table)
{
    static $groups = array(
        'products' => 'catalogue',
        'solutions' => 'catalogue',
        'solution_lines' => 'catalogue',
        'projects' => 'projects',
        'floors' => 'projects',
        'room_areas' => 'projects',
        'room_layers' => 'projects',
        'stock' => 'warehouse',
        'deliveries' => 'warehouse',
        'tasks' => 'site',
        'notes' => 'site',
        'material_uses' => 'site',
        'usage_logs' => 'site',
    );
    return isset($groups[$table]) ? $groups[$table] : 'owner';
}

function mm_perm_names()
{
    return array('catalogue', 'projects', 'warehouse', 'site');
}

/** A new worker: can count the shed and record site work, cannot change the catalogue or plans. */
function mm_default_perms()
{
    return array('catalogue' => false, 'projects' => false, 'warehouse' => true, 'site' => true);
}

class MmError extends Exception
{
}

function mm_fail($code)
{
    throw new MmError($code);
}

function mm_now()
{
    return (int) round(microtime(true) * 1000);
}

// ---- Storage ----------------------------------------------------------------------------------

function mm_config()
{
    static $config = null;
    if ($config === null) {
        $config = array();
        $file = __DIR__ . '/config.php';
        if (is_file($file)) {
            $loaded = include $file;
            if (is_array($loaded)) {
                $config = $loaded;
            }
        }
    }
    return $config;
}

function mm_is_mysql()
{
    $config = mm_config();
    return !empty($config['mysql']);
}

/** Keeps the data folder out of reach of a browser, on Apache hosts and elsewhere. */
function mm_protect_folder($dir)
{
    if (!is_file($dir . '/.htaccess')) {
        @file_put_contents(
            $dir . '/.htaccess',
            "<IfModule mod_authz_core.c>\n  Require all denied\n</IfModule>\n" .
            "<IfModule !mod_authz_core.c>\n  Order allow,deny\n  Deny from all\n</IfModule>\n"
        );
    }
    if (!is_file($dir . '/index.html')) {
        @file_put_contents($dir . '/index.html', '');
    }
}

function mm_db()
{
    static $pdo = null;
    if ($pdo !== null) {
        return $pdo;
    }
    $options = array(
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    );
    if (mm_is_mysql()) {
        $config = mm_config();
        $m = $config['mysql'];
        $dsn = 'mysql:host=' . $m['host'] . ';dbname=' . $m['database'] . ';charset=utf8mb4';
        if (!empty($m['port'])) {
            $dsn .= ';port=' . (int) $m['port'];
        }
        $pdo = new PDO($dsn, $m['user'], $m['password'], $options);
        $pdo->exec(
            'CREATE TABLE IF NOT EXISTS mm_meta (k VARCHAR(64) NOT NULL PRIMARY KEY, v TEXT) ' .
            'DEFAULT CHARSET=utf8mb4'
        );
        $pdo->exec(
            'CREATE TABLE IF NOT EXISTS mm_people (id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY, ' .
            'name VARCHAR(120) NOT NULL, owner INTEGER NOT NULL DEFAULT 0, perms TEXT NOT NULL, ' .
            'status VARCHAR(16) NOT NULL, code VARCHAR(16), token_hash VARCHAR(64), device VARCHAR(64), ' .
            'created BIGINT NOT NULL, joined BIGINT, seen BIGINT, INDEX mm_people_token (token_hash)) ' .
            'DEFAULT CHARSET=utf8mb4'
        );
        $pdo->exec(
            'CREATE TABLE IF NOT EXISTS mm_rows (tbl VARCHAR(40) NOT NULL, rid VARCHAR(24) NOT NULL, ' .
            'data MEDIUMTEXT, deleted INTEGER NOT NULL DEFAULT 0, seq BIGINT NOT NULL, dev VARCHAR(64), ' .
            'person INTEGER, at BIGINT NOT NULL, PRIMARY KEY (tbl, rid), INDEX mm_rows_seq (seq)) ' .
            'DEFAULT CHARSET=utf8mb4'
        );
    } else {
        $dir = __DIR__ . '/data';
        if (!is_dir($dir)) {
            @mkdir($dir, 0700, true);
        }
        mm_protect_folder($dir);
        $files = glob($dir . '/mixmaster-*.sqlite');
        $file = $files ? $files[0] : $dir . '/mixmaster-' . bin2hex(random_bytes(16)) . '.sqlite';
        $pdo = new PDO('sqlite:' . $file, null, null, $options);
        // Two phones saving at the same moment wait their turn rather than one being told no.
        $pdo->exec('PRAGMA busy_timeout = 15000');
        $pdo->exec('CREATE TABLE IF NOT EXISTS mm_meta (k TEXT NOT NULL PRIMARY KEY, v TEXT)');
        $pdo->exec(
            'CREATE TABLE IF NOT EXISTS mm_people (id INTEGER PRIMARY KEY AUTOINCREMENT, ' .
            'name TEXT NOT NULL, owner INTEGER NOT NULL DEFAULT 0, perms TEXT NOT NULL, ' .
            'status TEXT NOT NULL, code TEXT, token_hash TEXT, device TEXT, ' .
            'created INTEGER NOT NULL, joined INTEGER, seen INTEGER)'
        );
        $pdo->exec('CREATE INDEX IF NOT EXISTS mm_people_token ON mm_people (token_hash)');
        $pdo->exec(
            'CREATE TABLE IF NOT EXISTS mm_rows (tbl TEXT NOT NULL, rid TEXT NOT NULL, data TEXT, ' .
            'deleted INTEGER NOT NULL DEFAULT 0, seq INTEGER NOT NULL, dev TEXT, person INTEGER, ' .
            'at INTEGER NOT NULL, PRIMARY KEY (tbl, rid))'
        );
        $pdo->exec('CREATE INDEX IF NOT EXISTS mm_rows_seq ON mm_rows (seq)');
    }
    return $pdo;
}

/**
 * One writer at a time. The change counter is read and bumped inside it, so changes are numbered
 * in the order they were saved and a phone asking "what is new since 812" never misses one.
 */
function mm_write($work)
{
    $db = mm_db();
    if (mm_is_mysql()) {
        $db->beginTransaction();
        $db->exec("INSERT IGNORE INTO mm_meta (k, v) VALUES ('seq', '0')");
        $db->query("SELECT v FROM mm_meta WHERE k = 'seq' FOR UPDATE")->fetchAll();
    } else {
        $db->exec('BEGIN IMMEDIATE');
    }
    try {
        $result = $work($db);
        if (mm_is_mysql()) {
            $db->commit();
        } else {
            $db->exec('COMMIT');
        }
        return $result;
    } catch (Exception $e) {
        if (mm_is_mysql()) {
            $db->rollBack();
        } else {
            $db->exec('ROLLBACK');
        }
        throw $e;
    }
}

function mm_meta_get($key, $default = null)
{
    $st = mm_db()->prepare('SELECT v FROM mm_meta WHERE k = ?');
    $st->execute(array($key));
    $row = $st->fetch();
    return $row ? $row['v'] : $default;
}

function mm_meta_set($key, $value)
{
    $db = mm_db();
    $st = $db->prepare('UPDATE mm_meta SET v = ? WHERE k = ?');
    $st->execute(array((string) $value, $key));
    if ($st->rowCount() === 0 && mm_meta_get($key) === null) {
        $db->prepare('INSERT INTO mm_meta (k, v) VALUES (?, ?)')->execute(array($key, (string) $value));
    }
}

function mm_company()
{
    $id = mm_meta_get('company_id');
    if ($id === null) {
        return null;
    }
    return array('id' => $id, 'name' => mm_meta_get('company_name', ''));
}

// ---- People -----------------------------------------------------------------------------------

function mm_new_token()
{
    return bin2hex(random_bytes(24));
}

function mm_new_code()
{
    $alphabet = MM_CODE_ALPHABET;
    $code = '';
    $bytes = random_bytes(8);
    for ($i = 0; $i < 8; $i++) {
        $code .= $alphabet[ord($bytes[$i]) % 32];
    }
    return $code;
}

/** What people type is forgiving: spaces, dashes and small letters are all fine. */
function mm_clean_code($code)
{
    return strtoupper(preg_replace('/[^0-9A-Za-z]/', '', (string) $code));
}

function mm_perms_of($person)
{
    $perms = json_decode($person['perms'], true);
    if (!is_array($perms)) {
        $perms = mm_default_perms();
    }
    $out = array();
    foreach (mm_perm_names() as $name) {
        $out[$name] = !empty($person['owner']) || !empty($perms[$name]);
    }
    return $out;
}

function mm_me($person)
{
    return array(
        'id' => (int) $person['id'],
        'name' => $person['name'],
        'owner' => !empty($person['owner']),
        'perms' => mm_perms_of($person),
    );
}

function mm_person_out($person)
{
    return array(
        'id' => (int) $person['id'],
        'name' => $person['name'],
        'owner' => !empty($person['owner']),
        'perms' => mm_perms_of($person),
        'status' => $person['status'],
        'code' => $person['status'] === 'pending' ? $person['code'] : null,
        'joined' => $person['joined'] === null ? null : (int) $person['joined'],
        'seen' => $person['seen'] === null ? null : (int) $person['seen'],
    );
}

function mm_person($id)
{
    $st = mm_db()->prepare('SELECT * FROM mm_people WHERE id = ?');
    $st->execute(array((int) $id));
    $row = $st->fetch();
    return $row ? $row : null;
}

function mm_people_list()
{
    // MySQL sorts names without regard to case already; SQLite has to be asked.
    $byName = mm_is_mysql() ? 'name' : 'name COLLATE NOCASE';
    $rows = mm_db()->query('SELECT * FROM mm_people ORDER BY owner DESC, ' . $byName . ', id')->fetchAll();
    return array_map('mm_person_out', $rows);
}

function mm_clean_name($name)
{
    $name = trim(preg_replace('/\s+/u', ' ', (string) $name));
    if ($name === '') {
        mm_fail('bad_request');
    }
    return mb_substr($name, 0, 80, 'UTF-8');
}

function mm_clean_perms($perms)
{
    $out = mm_default_perms();
    if (is_array($perms)) {
        foreach (mm_perm_names() as $name) {
            if (array_key_exists($name, $perms)) {
                $out[$name] = (bool) $perms[$name];
            }
        }
    }
    return $out;
}

function mm_clean_device($device)
{
    $device = preg_replace('/[^0-9A-Za-z_-]/', '', (string) $device);
    return substr($device, 0, 64);
}

/**
 * The phone asking, from its token. Anything that no longer matches is told "revoked", and the
 * app takes that as the order to empty itself — unless the server now holds a different company
 * altogether (set up again from scratch), which is a mistake to report, not a sacking.
 */
function mm_auth($req)
{
    $company = mm_company();
    if ($company === null) {
        mm_fail('other_company');
    }
    if (isset($req['company']) && $req['company'] !== '' && $req['company'] !== $company['id']) {
        mm_fail('other_company');
    }
    $token = isset($req['token']) ? (string) $req['token'] : '';
    if ($token === '') {
        mm_fail('revoked');
    }
    $st = mm_db()->prepare('SELECT * FROM mm_people WHERE token_hash = ?');
    $st->execute(array(hash('sha256', $token)));
    $person = $st->fetch();
    if (!$person || $person['status'] !== 'active') {
        mm_fail('revoked');
    }
    $now = mm_now();
    if ($person['seen'] === null || $now - (int) $person['seen'] > 60000) {
        mm_db()->prepare('UPDATE mm_people SET seen = ? WHERE id = ?')->execute(array($now, $person['id']));
        $person['seen'] = $now;
    }
    return $person;
}

function mm_require_owner($person)
{
    if (empty($person['owner'])) {
        mm_fail('not_allowed');
    }
}

// ---- Actions ----------------------------------------------------------------------------------

function mm_hello($req)
{
    $company = mm_company();
    return array(
        'ok' => true,
        'app' => 'mixmaster',
        'protocol' => MM_PROTOCOL,
        'kind' => 'website',
        'claimed' => $company !== null,
        'company' => $company,
    );
}

function mm_setup($req)
{
    $companyName = mm_clean_name(isset($req['company_name']) ? $req['company_name'] : '');
    $name = mm_clean_name(isset($req['name']) ? $req['name'] : '');
    $device = mm_clean_device(isset($req['device']) ? $req['device'] : '');
    return mm_write(function ($db) use ($companyName, $name, $device) {
        if (mm_meta_get('company_id') !== null) {
            mm_fail('claimed');
        }
        $companyId = bin2hex(random_bytes(8));
        mm_meta_set('company_id', $companyId);
        mm_meta_set('company_name', $companyName);
        mm_meta_set('created', mm_now());
        $token = mm_new_token();
        $now = mm_now();
        $all = array();
        foreach (mm_perm_names() as $perm) {
            $all[$perm] = true;
        }
        $db->prepare(
            'INSERT INTO mm_people (name, owner, perms, status, code, token_hash, device, created, joined, seen) ' .
            "VALUES (?, 1, ?, 'active', NULL, ?, ?, ?, ?, ?)"
        )->execute(array($name, json_encode($all), hash('sha256', $token), $device, $now, $now, $now));
        $person = mm_person($db->lastInsertId());
        return array('ok' => true, 'token' => $token, 'company' => mm_company(), 'me' => mm_me($person));
    });
}

function mm_join($req)
{
    $code = mm_clean_code(isset($req['code']) ? $req['code'] : '');
    $device = mm_clean_device(isset($req['device']) ? $req['device'] : '');
    $result = mm_write(function ($db) use ($code, $device) {
        if (mm_company() === null) {
            mm_fail('not_claimed');
        }
        // Guessing codes is made slow: a few dozen wrong ones an hour, then nothing until the next.
        $hour = (int) floor(mm_now() / 3600000);
        $tries = explode(':', (string) mm_meta_get('join_fails', '0:0'));
        $fails = ((int) $tries[0] === $hour) ? (int) (isset($tries[1]) ? $tries[1] : 0) : 0;
        if ($fails >= MM_JOIN_TRIES_PER_HOUR) {
            return array('ok' => false, 'error' => 'too_many');
        }
        $person = null;
        if (strlen($code) === 8) {
            $st = $db->prepare("SELECT * FROM mm_people WHERE status = 'pending' AND code = ?");
            $st->execute(array($code));
            $person = $st->fetch();
        }
        if (!$person) {
            mm_meta_set('join_fails', $hour . ':' . ($fails + 1));
            return array('ok' => false, 'error' => 'bad_code');
        }
        $token = mm_new_token();
        $now = mm_now();
        $db->prepare(
            "UPDATE mm_people SET status = 'active', code = NULL, token_hash = ?, device = ?, joined = ?, seen = ? WHERE id = ?"
        )->execute(array(hash('sha256', $token), $device, $now, $now, $person['id']));
        $person = mm_person($person['id']);
        return array('ok' => true, 'token' => $token, 'company' => mm_company(), 'me' => mm_me($person));
    });
    return $result;
}

function mm_row_out($row)
{
    return array(
        't' => $row['tbl'],
        'id' => (string) $row['rid'],
        'd' => ((int) $row['deleted']) ? null : $row['data'],
        'x' => (bool) (int) $row['deleted'],
        'dev' => $row['dev'],
    );
}

function mm_pull($req)
{
    $person = mm_auth($req);
    $since = isset($req['since']) ? (int) $req['since'] : 0;
    $st = mm_db()->prepare('SELECT * FROM mm_rows WHERE seq > ? ORDER BY seq LIMIT ' . (MM_PAGE + 1));
    $st->execute(array($since));
    $rows = $st->fetchAll();
    $more = count($rows) > MM_PAGE;
    if ($more) {
        array_pop($rows);
    }
    $next = $since;
    $out = array();
    foreach ($rows as $row) {
        $out[] = mm_row_out($row);
        $next = max($next, (int) $row['seq']);
    }
    return array(
        'ok' => true,
        'company' => mm_company(),
        'me' => mm_me($person),
        'rows' => $out,
        'next' => $next,
        'more' => $more,
    );
}

function mm_may_write($person, $table)
{
    if (!empty($person['owner'])) {
        return true;
    }
    $group = mm_group($table);
    if ($group === 'owner') {
        return false;
    }
    $perms = mm_perms_of($person);
    return !empty($perms[$group]);
}

function mm_push($req)
{
    $person = mm_auth($req);
    $rows = isset($req['rows']) && is_array($req['rows']) ? $req['rows'] : array();
    if (count($rows) > MM_MAX_PUSH) {
        mm_fail('bad_request');
    }
    $device = mm_clean_device(isset($req['device']) ? $req['device'] : '');
    $refused = mm_write(function ($db) use ($rows, $person, $device) {
        $refused = array();
        $seq = (int) mm_meta_get('seq', '0');
        $find = $db->prepare('SELECT * FROM mm_rows WHERE tbl = ? AND rid = ?');
        $update = $db->prepare('UPDATE mm_rows SET data = ?, deleted = ?, seq = ?, dev = ?, person = ?, at = ? WHERE tbl = ? AND rid = ?');
        $insert = $db->prepare('INSERT INTO mm_rows (tbl, rid, data, deleted, seq, dev, person, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
        $now = mm_now();
        foreach ($rows as $row) {
            if (!is_array($row)) {
                continue;
            }
            $table = isset($row['t']) ? (string) $row['t'] : '';
            $id = isset($row['id']) ? (string) $row['id'] : '';
            if (!preg_match('/^[a-z][a-z0-9_]{0,39}$/', $table) || !preg_match('/^-?[0-9]{1,20}$/', $id)) {
                continue;
            }
            $deleted = !empty($row['x']);
            $data = $deleted ? null : (isset($row['d']) ? (string) $row['d'] : null);
            if (!$deleted && ($data === null || strlen($data) > 1000000)) {
                continue;
            }
            $find->execute(array($table, $id));
            $current = $find->fetch();
            $find->closeCursor();
            if (!mm_may_write($person, $table)) {
                // Not theirs to change: the company's own copy goes back, and the phone puts it back.
                $refused[] = $current ? mm_row_out($current) : array('t' => $table, 'id' => $id, 'd' => null, 'x' => true, 'dev' => null);
                continue;
            }
            $seq++;
            if ($current) {
                $update->execute(array($data, $deleted ? 1 : 0, $seq, $device, (int) $person['id'], $now, $table, $id));
            } else {
                $insert->execute(array($table, $id, $data, $deleted ? 1 : 0, $seq, $device, (int) $person['id'], $now));
            }
        }
        mm_meta_set('seq', $seq);
        return $refused;
    });
    return array('ok' => true, 'company' => mm_company(), 'me' => mm_me($person), 'refused' => $refused);
}

function mm_people($req)
{
    $person = mm_auth($req);
    mm_require_owner($person);
    return array('ok' => true, 'me' => mm_me($person), 'people' => mm_people_list());
}

function mm_person_save($req)
{
    $me = mm_auth($req);
    mm_require_owner($me);
    $in = isset($req['person']) && is_array($req['person']) ? $req['person'] : array();
    $name = mm_clean_name(isset($in['name']) ? $in['name'] : '');
    $owner = !empty($in['owner']);
    $perms = mm_clean_perms(isset($in['perms']) ? $in['perms'] : null);
    $id = isset($in['id']) ? (int) $in['id'] : 0;
    $saved = mm_write(function ($db) use ($me, $name, $owner, $perms, $id) {
        if ($id > 0) {
            $person = mm_person($id);
            if (!$person) {
                mm_fail('not_found');
            }
            // Nobody takes their own keys away by accident: another owner has to do it.
            $keepOwner = ((int) $person['id'] === (int) $me['id']) ? 1 : ($owner ? 1 : 0);
            $db->prepare('UPDATE mm_people SET name = ?, owner = ?, perms = ? WHERE id = ?')
                ->execute(array($name, $keepOwner, json_encode($perms), $id));
            return mm_person($id);
        }
        $db->prepare(
            'INSERT INTO mm_people (name, owner, perms, status, code, token_hash, device, created, joined, seen) ' .
            "VALUES (?, ?, ?, 'pending', ?, NULL, NULL, ?, NULL, NULL)"
        )->execute(array($name, $owner ? 1 : 0, json_encode($perms), mm_new_code(), mm_now()));
        return mm_person($db->lastInsertId());
    });
    return array('ok' => true, 'person' => mm_person_out($saved), 'people' => mm_people_list());
}

/** A fresh code for someone: whatever phone they had is cut off, and the new code gets them back in. */
function mm_person_code($req)
{
    $me = mm_auth($req);
    mm_require_owner($me);
    $id = isset($req['id']) ? (int) $req['id'] : 0;
    if ($id === (int) $me['id']) {
        mm_fail('not_allowed');
    }
    $saved = mm_write(function ($db) use ($id) {
        if (!mm_person($id)) {
            mm_fail('not_found');
        }
        $db->prepare("UPDATE mm_people SET status = 'pending', code = ?, token_hash = NULL, device = NULL WHERE id = ?")
            ->execute(array(mm_new_code(), $id));
        return mm_person($id);
    });
    return array('ok' => true, 'person' => mm_person_out($saved), 'people' => mm_people_list());
}

function mm_person_remove($req)
{
    $me = mm_auth($req);
    mm_require_owner($me);
    $id = isset($req['id']) ? (int) $req['id'] : 0;
    if ($id === (int) $me['id']) {
        mm_fail('not_allowed');
    }
    mm_write(function ($db) use ($id) {
        $db->prepare('DELETE FROM mm_people WHERE id = ?')->execute(array($id));
    });
    return array('ok' => true, 'people' => mm_people_list());
}

/** A phone giving its place back. The last owner cannot: the company would have nobody to run it. */
function mm_leave($req)
{
    $me = mm_auth($req);
    mm_write(function ($db) use ($me) {
        if (!empty($me['owner'])) {
            $owners = (int) $db->query("SELECT count(*) AS n FROM mm_people WHERE owner = 1 AND status = 'active'")->fetch()['n'];
            if ($owners <= 1) {
                mm_fail('last_owner');
            }
        }
        $db->prepare("UPDATE mm_people SET status = 'left', code = NULL, token_hash = NULL, device = NULL WHERE id = ?")
            ->execute(array($me['id']));
    });
    return array('ok' => true);
}

function mm_handle($req)
{
    $action = isset($req['a']) ? (string) $req['a'] : '';
    switch ($action) {
        case 'hello':
            return mm_hello($req);
        case 'setup':
            return mm_setup($req);
        case 'join':
            return mm_join($req);
        case 'pull':
            return mm_pull($req);
        case 'push':
            return mm_push($req);
        case 'people':
            return mm_people($req);
        case 'person_save':
            return mm_person_save($req);
        case 'person_code':
            return mm_person_code($req);
        case 'person_remove':
            return mm_person_remove($req);
        case 'leave':
            return mm_leave($req);
    }
    mm_fail('bad_request');
}

// ---- The door ---------------------------------------------------------------------------------

function mm_status_page()
{
    header('Content-Type: text/html; charset=utf-8');
    $state = 'The server is running, but it cannot open its database: ask the host whether PHP has SQLite (pdo_sqlite).';
    try {
        $company = mm_company();
        $state = $company === null
            ? 'The server is running and waiting to be set up. Open MixMaster on the employer\'s phone: Settings → Company → Set up.'
            : 'The server is running and set up for ' . htmlspecialchars($company['name'], ENT_QUOTES, 'UTF-8') . '.';
    } catch (Exception $e) {
        error_log('MixMaster: ' . $e->getMessage());
    }
    echo '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">' .
        '<title>MixMaster server</title><body style="font-family:sans-serif;max-width:32em;margin:3em auto;padding:0 1em">' .
        '<h1>MixMaster</h1><p>' . $state . '</p></body>';
}

function mm_main()
{
    if (!isset($_SERVER['REQUEST_METHOD']) || $_SERVER['REQUEST_METHOD'] !== 'POST') {
        mm_status_page();
        return;
    }
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    try {
        $raw = file_get_contents('php://input', false, null, 0, MM_MAX_BODY + 1);
        if ($raw === false || strlen($raw) > MM_MAX_BODY) {
            mm_fail('bad_request');
        }
        $req = json_decode($raw, true);
        if (!is_array($req)) {
            mm_fail('bad_request');
        }
        $out = mm_handle($req);
    } catch (MmError $e) {
        $out = array('ok' => false, 'error' => $e->getMessage());
    } catch (Exception $e) {
        error_log('MixMaster: ' . $e->getMessage());
        $out = array('ok' => false, 'error' => 'server');
    } catch (Error $e) {
        error_log('MixMaster: ' . $e->getMessage());
        $out = array('ok' => false, 'error' => 'server');
    }
    echo json_encode($out, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}

mm_main();
