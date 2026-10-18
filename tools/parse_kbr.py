"""Parse the yearly DUM KBR prayer schedule PDF into the app's JSON format.

Usage: python -I parse_kbr.py <schedule.pdf> <out.json> <year>
"""
import datetime
import json
import re
import sys

import pymupdf

MONTHS = ['ЯНВАРЬ', 'ФЕВРАЛЬ', 'МАРТ', 'АПРЕЛЬ', 'МАЙ', 'ИЮНЬ', 'ИЮЛЬ', 'АВГУСТ', 'СЕНТЯБРЬ', 'ОКТЯБРЬ', 'НОЯБРЬ', 'ДЕКАБРЬ']
T = r'(\d{1,2}[:.]\d{2})'
ROW = re.compile(r'(?<!\d)(\d{1,2})\s+(?:Пн|Вт|Ср|Чт|Пт|Сб|Вс)\s+' + r'\s+'.join([T] * 6))

pdf, out, year = sys.argv[1], sys.argv[2], int(sys.argv[3])
days = {}
for page in pymupdf.open(pdf):
    text = page.get_text()
    m = re.search(r'НА\s+(' + '|'.join(MONTHS) + r')\s+(\d{4})', text.upper())
    assert m, 'month header not found'
    month = MONTHS.index(m.group(1)) + 1
    assert int(m.group(2)) == year, m.group(2)
    for r in ROW.finditer(text):
        d = int(r.group(1))
        date = datetime.date(year, month, d)
        times = [t.replace('.', ':').zfill(5) for t in r.groups()[1:]]
        # Times must be strictly increasing within a day.
        assert times == sorted(times), (date, times)
        days[date.isoformat()] = times

# Every day of the year must be present exactly once.
start = datetime.date(year, 1, 1)
expected = {(start + datetime.timedelta(i)).isoformat() for i in range((datetime.date(year + 1, 1, 1) - start).days)}
missing = sorted(expected - days.keys())
assert not missing, f'missing days: {missing[:10]} ({len(missing)})'

json.dump({
    'source': 'Духовное управление мусульман КБР (kbrdum.ru)',
    'region': 'Кабардино-Балкарская Республика',
    'zone': 'Europe/Moscow',
    'year': year,
    'order': ['FAJR', 'SUNRISE', 'DHUHR', 'ASR', 'MAGHRIB', 'ISHA'],
    'days': dict(sorted(days.items())),
}, open(out, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('days', len(days), '| 2026-10-18 ->', days.get(f'{year}-10-18'))
