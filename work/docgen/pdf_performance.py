"""Performance Testing Document — PDF."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pdf_style import (bullets, document, h1, h2, metrics, note, p, pill,
                       section, table)
from render_pdf import to_pdf

OUT = os.path.join(os.path.expanduser('~'), 'Downloads',
                   'Pixous_HR_Portal_Performance_Testing_v1.0.pdf')

PASS = lambda: pill('pass', 'Pass')

body = ''.join([

section(
  h1('1.', 'Objective and Method'),
  p('The objective was to establish where time is actually spent when somebody '
    'uses the HR Portal, apply what could be improved, and state plainly what '
    'cannot be. No figure in this document is estimated: each is a measurement, '
    'and the command that produced it is given so it can be repeated.'),

  metrics([('0.45 ms', 'page, server-side'), ('6–9 ms', 'api, server-side'),
           ('620 ms', 'end-to-end from India'), ('98%', 'of that is network')]),

  h2('1.1  Method'),
  bullets([
    'Client-side timings taken with curl over the public internet from '
    'Coimbatore, using --write-out to separate DNS, TCP, TLS and '
    'time-to-first-byte.',
    'Server-side timings taken with curl on the EC2 host itself against '
    'localhost, which removes the network entirely and leaves only the '
    'application.',
    'Database latency measured as a timed TCP connect from the application '
    'container to the database host.',
    'Resource headroom read from uptime, free and docker stats.',
    'Each timing sampled at least three times; ranges are reported rather than '
    'single values.',
  ]),
),

section(
  h1('2.', 'Baseline Measurements'),
  h2('2.1  End-to-end from a client in India'),
  table(['Sample', 'TCP connect', 'TLS complete', 'First byte', 'Total'],
        [['1', '0.217 s', '0.435 s', '0.638 s', '0.638 s'],
         ['2', '0.211 s', '0.422 s', '0.621 s', '0.621 s'],
         ['3', '0.186 s', '0.380 s', '0.564 s', '0.564 s'],
         ['4', '0.205 s', '0.417 s', '0.625 s', '0.625 s'],
         ['5', '0.203 s', '0.419 s', '0.646 s', '0.646 s'],
         ['Mean', '0.204 s', '0.415 s', '0.619 s', '0.619 s']],
        widths=['14%', '22%', '22%', '21%', '21%'], total_last=True),
  note('curl -w "%{time_connect} %{time_appconnect} %{time_starttransfer}" '
       'https://pixoushrportal.pixous.info/'),

  h2('2.2  The same request measured on the server'),
  table(['Target', 'Sample 1', 'Sample 2', 'Sample 3', 'Verdict'],
        [['Static page (/)', '0.53 ms', '0.45 ms', '0.41 ms',
          'Effectively instant'],
         ['API (/api/dashboard/me)', '8.6 ms', '6.4 ms', '9.6 ms', 'Fast']],
        widths=['28%', '15%', '15%', '15%', '27%']),
  note('Run as: curl -w "%{time_starttransfer}" http://localhost/ — on the EC2 '
       'host, so no network is involved.'),

  h2('2.3  What that comparison shows'),
  p('The application answers a page in half a millisecond and an API call in '
    'six to ten milliseconds. The same requests take 619 ms from India. The '
    'difference is not the application.'),
  table(['Phase', 'Time', 'Share', 'Cause'],
        [['TCP handshake', '~204 ms', '33%', 'One round trip to Paris'],
         ['TLS handshake', '~211 ms', '34%',
          'One round trip — TLS 1.3, already the minimum'],
         ['Request and response', '~204 ms', '33%',
          'One round trip plus under 10 ms of server work'],
         ['Application work', 'under 10 ms', 'under 2%',
          'The only part that code can change']],
        widths=['24%', '15%', '11%', '50%']),
  p('Roughly 610 ms of the 619 ms is the time light takes to cross the distance '
    'three times. The round-trip time between client and server is about '
    '200 ms, and TLS 1.3 already uses the fewest round trips a first connection '
    'can.'),
),

section(
  h1('3.', 'Transport and Delivery — Verified'),
  table(['Optimisation', 'Status', 'Evidence'],
        [['HTTP/2', 'Active',
          'openssl s_client -alpn h2 returns "ALPN protocol: h2"'],
         ['TLS 1.3', 'Active', 'Protocol TLSv1.3, TLS_AES_256_GCM_SHA384'],
         ['TLS session tickets', 'Enabled', 'ssl_session_tickets on'],
         ['TLS early data', 'Enabled', 'ssl_early_data on'],
         ['gzip compression', 'Active',
          'Page drops from 1,955 to 971 bytes — 50%'],
         ['Keep-alive', 'Enabled',
          'keepalive_timeout 300s, keepalive_requests 1000'],
         ['Immutable asset caching', 'Enabled',
          '/assets and /fonts served with long max-age and immutable'],
         ['index.html no-cache', 'Enabled',
          'Cache-Control: no-cache, so a deployment is picked up at once'],
         ['Self-hosted fonts', 'Active',
          '23 woff2 files served from origin; no third-party font request'],
         ['Service-worker precache', 'Active',
          '124 entries, 2.1 MB — repeat visits load from disk']],
        widths=['26%', '13%', '61%']),
  p('These were verified rather than assumed. An earlier check appeared to show '
    'HTTP/1.1; repeating it with ALPN offered showed h2 negotiating correctly, '
    'so the first result was a fault in the measurement and not in the server.'),
),

section(
  h1('4.', 'Server Capacity'),
  metrics([('0.07', 'load average'), ('5,168 MB', 'memory available'),
           ('2.65%', 'backend cpu'), ('12 days', 'uptime')]),
  table(['Metric', 'Value', 'Assessment'],
        [['Load average (1 / 5 / 15 min)', '0.07 / 0.06 / 0.06',
          'Essentially idle'],
         ['Memory total', '7,777 MB', ''],
         ['Memory available', '5,168 MB', 'Two thirds free'],
         ['nginx container CPU', '0.00%', 'Negligible'],
         ['backend container CPU', '2.65%', 'Low'],
         ['backend container memory', '683 MB', 'Comfortable'],
         ['mysql container CPU', '0.53%', 'Low'],
         ['analytics container memory', '507 MB', 'Comfortable']],
        widths=['33%', '23%', '44%']),
  p('There is no capacity problem. The server is not the constraint at current '
    'load, and adding CPU or memory would change nothing.'),
),

section(
  h1('5.', 'The Database — The Real Application-Side Constraint'),
  p('MySQL is not on the application host. It is on external shared hosting, '
    'and the connection cost was measured from inside the backend container.'),
  table(['Sample', 'TCP connect to database', 'Note'],
        [['1', '127 ms', ''], ['2', '124 ms', ''], ['3', '123 ms', ''],
         ['Mean', '125 ms', 'Paid on every new connection']],
        widths=['14%', '30%', '56%'], total_last=True),
  p('A query on a pooled connection does not pay this; establishing a new one '
    'does. The hosted MySQL closes an idle connection after 30 seconds '
    '(wait_timeout), so the pool must recycle within that window — which means '
    'new connections are frequent by design.'),

  h2('5.1  A defect this exposed'),
  p('The application log carried seven "Communications link failure" errors: a '
    'connection the host had already closed was being handed to a query, and '
    'the request failed rather than the connection being quietly replaced. That '
    'is the expensive kind of failure, because the retry pays the 125 ms again.'),

  h2('5.2  What was changed, and what could not be'),
  table(['Setting', 'Value', 'Effect'],
        [['validation-timeout', '1,000 ms',
          'Applied. A connection that cannot be validated within a second '
          'against a 125 ms hop is replaced rather than waited on.'],
         ['keepalive-time', 'Not set',
          'Cannot be used here. Hikari requires at least 30,000 ms AND less '
          'than max-lifetime, which is 30,000 ms because that is the host’s '
          'wait_timeout. Both cannot hold, so Hikari logs that it is disabling '
          'the value and ignores it.'],
         ['max-lifetime', '30,000 ms',
          'Unchanged. Raising it to make room for keepalive would mean holding '
          'connections the host has already closed — the fault this was meant '
          'to prevent.'],
         ['maximum-pool-size', '6 in production',
          'Unchanged. The shared hosting account allows twenty connections in '
          'total across every process, so a larger pool risks locking the '
          'portal out of its own database during a deployment.']],
        widths=['20%', '15%', '65%']),
  note('The keepalive limitation was found by reading the running server’s log '
       'after deploying the change, not by assuming it worked. The setting was '
       'then removed and the reasoning recorded in application.yml.'),

  h2('5.3  Result after the change'),
  table(['Check', 'Before', 'After'],
        [['Communications link failure', '7 occurrences', '0'],
         ['Failed to validate connection', 'present', '0'],
         ['API response, server-side', '12 ms', '6–9 ms'],
         ['Backend health', 'healthy', 'healthy']],
        widths=['40%', '30%', '30%']),
  note('"After" counted from the application log since the restart that '
       'deployed the change.'),
),

section(
  h1('6.', 'Query Efficiency — Already Addressed'),
  table(['Setting', 'Value', 'Why it matters'],
        [['hibernate.default_batch_fetch_size', '50',
          'Roles are EAGER on User and permissions EAGER on Role, so listing '
          'people cost one query per person plus one per role — 112 queries to '
          'return six employees. Batching gathers them fifty owners at a time.'],
         ['hibernate.jdbc.fetch_size', '200',
          'Rows per network round trip. The default of ten means a hundred rows '
          'cross the wire in ten trips, which is free locally and is not '
          'against a database in another datacentre.'],
         ['hibernate.jdbc.batch_size', '50',
          'Batches inserts and updates.'],
         ['query.in_clause_parameter_padding', 'true',
          'Pads IN clauses to fixed sizes so the statement cache is reused '
          'instead of compiling a new plan per parameter count.'],
         ['spring.jpa.open-in-view', 'false',
          'Prevents a session being held open for the whole request, which '
          'hides N+1 queries behind lazy loading in the view layer.'],
         ['Redis cache', 'Enabled',
          'Reads served from cache do not cross the 125 ms hop at all.']],
        widths=['30%', '11%', '59%']),
),

section(
  h1('7.', 'The Change That Would Matter Most'),
  p('Since roughly 98% of the time a user waits is network distance, the only '
    'change that materially improves perceived speed is moving the server '
    'closer to the users.'),
  table(['Scenario', 'RTT', 'Expected first byte', 'Change'],
        [['Today — eu-west-3 (Paris)', '~200 ms', '~619 ms', 'baseline'],
         ['ap-south-1 (Mumbai)', '~30–40 ms', '~110–130 ms',
          'about 5× faster'],
         ['Mumbai plus database on the same host', '~30–40 ms', '~100–120 ms',
          'removes the 125 ms database hop as well']],
        widths=['36%', '14%', '24%', '26%']),
  p('The Mumbai figures are arithmetic from a typical India-to-Mumbai round '
    'trip, not a measurement — there is no server there to measure. They are '
    'included because the structure of the result is reliable even if the exact '
    'number is not: three round trips at 35 ms is about 105 ms, against three '
    'at 200 ms.'),
  p('This migration is blocked on one thing: the database backup is manual, '
    'unscheduled, and kept as a single copy on the same server being moved. '
    'That should be fixed first.'),

  h1('8.', 'Conclusion'),
  bullets([
    'The application is fast. Server-side, a page takes 0.4–0.5 ms and an API '
    'call 6–9 ms.',
    'The server is not the constraint: load 0.07, two thirds of memory free, '
    'CPU under 3%.',
    'Transport is already optimal — HTTP/2, TLS 1.3, gzip, keep-alive, '
    'immutable caching, self-hosted fonts, service-worker precache — all '
    'verified rather than assumed.',
    'The 619 ms a user experiences is about 98% network distance to Paris. No '
    'code change can reduce it.',
    'One real application-side fault was found and fixed: dead pooled '
    'connections reaching queries. Link failures went from seven to zero.',
    'The highest-value performance work remaining is not code. It is moving the '
    'server to Mumbai, and that is blocked on making the backups safe first.',
  ]),
),
])

html = document(
    'Performance Testing Document',
    'Performance Engineering',
    'Measured response times, the bottleneck analysis they support, and the '
    'optimisations applied to the production system.',
    body)

print(to_pdf(html, OUT))
