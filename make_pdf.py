# -*- coding: utf-8 -*-
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table,
                                TableStyle, ListFlowable, ListItem)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

# Korean font
pdfmetrics.registerFont(TTFont('KR', '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc', subfontIndex=0))

GREEN = colors.HexColor('#00c281')
GREENBG = colors.HexColor('#e9fff7')
DARK = colors.HexColor('#1a1a1a')
RED = colors.HexColor('#c0392b')
CODEBG = colors.HexColor('#f4f4f4')
GREY = colors.HexColor('#777777')

styles = getSampleStyleSheet()

def S(name, **kw):
    base = dict(fontName='KR', textColor=DARK, leading=14, fontSize=9.5)
    base.update(kw)
    return ParagraphStyle(name, **base)

h1 = S('h1', fontSize=18, leading=22, textColor=colors.HexColor('#111111'))
sub = S('sub', fontSize=9, textColor=GREY, leading=12)
h2 = S('h2', fontSize=12.5, leading=16, textColor=colors.HexColor('#063a2a'))
body = S('body', fontSize=9.5, leading=14)
small = S('small', fontSize=8.5, leading=12)
cellh = S('cellh', fontSize=8.5, leading=11, textColor=colors.HexColor('#222222'))
cell = S('cell', fontSize=8.5, leading=11)
foot = S('foot', fontSize=7.5, textColor=GREY, leading=10)

def code(t):
    return '<font face="Courier" size="8.5" color="#b5008a">%s</font>' % t

def b(t):
    return '<b>%s</b>' % t

doc = SimpleDocTemplate('/home/user/freeze/chzzk_css_fix.pdf', pagesize=A4,
                        topMargin=14*mm, bottomMargin=12*mm,
                        leftMargin=15*mm, rightMargin=15*mm)
E = []

def heading(txt):
    tbl = Table([[Paragraph(txt, S('hh', fontSize=12.5, leading=15,
                 textColor=colors.HexColor('#063a2a')))]],
                colWidths=[180*mm])
    tbl.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), GREEN),
        ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
        ('TOPPADDING',(0,0),(-1,-1),4),('BOTTOMPADDING',(0,0),(-1,-1),4),
        ('ROUNDEDCORNERS',[4,4,4,4]),
    ]))
    return tbl

E.append(Paragraph('치지직 채팅 오버레이 CSS — 문제점과 해결 방법', h1))
E.append(Paragraph('OBS 브라우저 소스용 채팅 커스텀 CSS가 갑자기 안 먹을 때', sub))
E.append(Spacer(1, 8))

E.append(heading('1. 무슨 일이 일어났나'))
E.append(Spacer(1, 4))
E.append(Paragraph('어느 날 갑자기 채팅 디자인(흰 말풍선 · 닉네임 숨김 · 글자색 등)이 '
    + b('전부 사라지고') + ' 치지직 기본 채팅으로 나옵니다. CSS 내용은 그대로인데 '
    '아무것도 적용되지 않습니다.', body))
E.append(Spacer(1, 8))

E.append(heading('2. 원인 — 치지직이 클래스 이름을 바꿈'))
E.append(Spacer(1, 4))
E.append(Paragraph('CSS는 ' + code('live_chatting_message_wrapper') + ' 처럼 '
    + b('의미 있는 이름') + '으로 채팅 요소를 찾습니다. 그런데 치지직이 업데이트하면서 이름을 '
    + code('_item_f6gts_20') + ' 처럼 ' + b('해시(임의 문자열)가 붙은 이름') + '으로 바꿨습니다.', body))
E.append(Spacer(1, 4))

data = [
    [Paragraph('기존 (지금 안 먹는 이름)', cellh), Paragraph('바뀐 실제 이름', cellh)],
    [Paragraph(code('live_overlay_item'), cell), Paragraph(code('_item_f6gts_20'), cell)],
    [Paragraph(code('live_chatting_message_text'), cell), Paragraph(code('_text_1s877_1'), cell)],
    [Paragraph(code('live_chatting_username_...'), cell), Paragraph(code('_container_o04z9_2'), cell)],
]
t = Table(data, colWidths=[90*mm, 90*mm])
t.setStyle(TableStyle([
    ('GRID',(0,0),(-1,-1),0.5,colors.HexColor('#cccccc')),
    ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#f0f0f0')),
    ('TOPPADDING',(0,0),(-1,-1),4),('BOTTOMPADDING',(0,0),(-1,-1),4),
    ('LEFTPADDING',(0,0),(-1,-1),6),
]))
E.append(t)
E.append(Spacer(1, 4))
E.append(Paragraph('기존 CSS는 ' + code('[class^=live_...]') + ' (' + b('~로 시작') +
    ') 으로 찾는데, 새 이름은 ' + code('live_') + '로 시작하지 않으니 '
    '<font color="#c0392b"><b>하나도 못 잡아서</b></font> 스타일이 통째로 죽은 것입니다.', body))
E.append(Spacer(1, 8))

E.append(heading('3. 해결 방법'))
E.append(Spacer(1, 4))
E.append(Paragraph('셀렉터를 ' + b('"이름으로 시작"') + ' 에서 ' + b('"이름 일부 포함"') +
    ' 으로 바꿉니다.', body))
E.append(Spacer(1, 3))
boxdata = [[Paragraph('<font color="#c0392b"><b>기존:</b></font> ' + code('[class^=live_overlay_item]'), small)],
           [Paragraph('<font color="#1a8f4c"><b>수정:</b></font> ' + code('[class*="_item_"]'), small)]]
bt = Table(boxdata, colWidths=[180*mm])
bt.setStyle(TableStyle([
    ('BACKGROUND',(0,0),(-1,-1),GREENBG),
    ('BOX',(0,0),(-1,-1),0.5,GREEN),
    ('TOPPADDING',(0,0),(-1,-1),3),('BOTTOMPADDING',(0,0),(-1,-1),3),
    ('LEFTPADDING',(0,0),(-1,-1),8),
]))
E.append(bt)
E.append(Spacer(1, 4))

data2 = [
    [Paragraph('용도', cellh), Paragraph('수정 후 셀렉터', cellh)],
    [Paragraph('채팅 전체 컨테이너', cell), Paragraph(code('[class*="_chatting_"]'), cell)],
    [Paragraph('채팅 한 줄 (말풍선)', cell), Paragraph(code('[class*="_item_"]'), cell)],
    [Paragraph('메시지 글자', cell), Paragraph(code('[class*="_text_"]'), cell)],
    [Paragraph('닉네임 숨기기', cell), Paragraph(code('[class*="_wrapper_"] &gt; [class*="_container_"]:first-child'), cell)],
]
t2 = Table(data2, colWidths=[55*mm, 125*mm])
t2.setStyle(TableStyle([
    ('GRID',(0,0),(-1,-1),0.5,colors.HexColor('#cccccc')),
    ('BACKGROUND',(0,0),(-1,0),colors.HexColor('#f0f0f0')),
    ('TOPPADDING',(0,0),(-1,-1),4),('BOTTOMPADDING',(0,0),(-1,-1),4),
    ('LEFTPADDING',(0,0),(-1,-1),6),
]))
E.append(t2)
E.append(Spacer(1, 8))

E.append(heading('4. 왜 이 방법이 더 안전한가'))
E.append(Spacer(1, 4))
items = [
    '해시(' + code('f6gts') + ', ' + code('o04z9') + ')는 치지직이 업데이트할 때마다 ' + b('또 바뀝니다.'),
    code('[class*="_item_"]') + ' 처럼 ' + b('의미 부분만') + ' 잡으면 해시가 바뀌어도 계속 작동합니다.',
    '닉네임은 이름이 아니라 ' + b('위치') + '(글자 바로 앞 요소)로 잡아서 더 잘 버팁니다.',
]
E.append(ListFlowable([ListItem(Paragraph(x, body), leftIndent=10) for x in items],
                      bulletType='bullet', start='•', leftIndent=14))
E.append(Spacer(1, 6))

tip = [[Paragraph(b('다음에 또 깨지면?') + ' 브라우저에서 채팅 글자를 ' + b('우클릭 → 검사(F12)') +
    ' 로 새 클래스 이름을 확인하고, 위 표의 셀렉터를 그 이름에 맞게 고치면 됩니다.', small)]]
tt = Table(tip, colWidths=[180*mm])
tt.setStyle(TableStyle([
    ('BACKGROUND',(0,0),(-1,-1),GREENBG),
    ('BOX',(0,0),(-1,-1),0.5,GREEN),
    ('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),
    ('LEFTPADDING',(0,0),(-1,-1),10),('RIGHTPADDING',(0,0),(-1,-1),10),
]))
E.append(tt)
E.append(Spacer(1, 10))
E.append(Paragraph('치지직(Chzzk) 채팅 오버레이 커스텀 CSS 정리 · 2026', foot))

doc.build(E)
print('PDF created')
