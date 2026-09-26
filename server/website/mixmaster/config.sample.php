<?php
/*
 * Only needed on a host without SQLite. Copy this file to config.php (same folder), fill in the
 * database the hosting control panel gives you, and upload it. With no config.php the server
 * keeps everything in a file in data/ instead, which is the simpler of the two.
 */
return array(
    'mysql' => array(
        'host' => 'localhost',
        'port' => 3306,
        'database' => 'mixmaster',
        'user' => 'mixmaster',
        'password' => 'put the database password here',
    ),
);
