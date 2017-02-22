import re

pattern_numbers_re = re.compile('^[,.0-9]*[0-9][,.0-9]*$')
CURRENCY = 'CURRENCY'
NUMBER = '###'
UNK = '_'

number_words = ['_', 'usd', 'aud', 'gbp', 'ind', 'inr', 'euro', 'indian',
                'uk', 'us', 'k', 'm', 'zero', 'one', 'two', 'three', 'four', 'five',
                'six', 'seven', 'eight', 'nine', 'ten', 'twenty', 'thirty', 'forty',
                'fifty', 'sixty', 'seventy', 'eighty', 'ninety', 'hundred',
                'thousand', 'million', 'dollars']

def fill_dict_with_lists(dictionary, lists):
    for sublist in lists:
        name = sublist[-1]
        for alias in sublist:
            dictionary[alias] = name

#    Post annotation
__currency__ = [
    ["adsense"],
    ["amazon"],
    ["ap", "alterpay"],
    ["bank", "deposit", "bank deposit"],
    ["bloocoins", "bloocoin"],
    ["btc", "bitcoin", "bit", "coin", "coins", "btc-e", "bitcoins", "bitcoin"],
    ["cage", "cagecoin"],
    ["cashu"],
    ["catcoin"],
    ["cc", "credit", "credit card"],
    ["coye", "coinye"],
    ["doge", "doges", "dogecoin", "doge coins", "doge coin", "dogecoins"],
    ["dwolla"],
    ["ego", "egopay"],
    ["flappycoins", "flappycoin"],
    ["freelancers"],
    ["interac"],
    ["liberty", "reserve", "l.r", "lr", "libertyreserve", "liberty reserve"],
    ["ltc", "lite-coin", "lite-coints", "litecoins", "litecoin"],
    ["mb", "moneybooker", "booker", "moneybrokers", "moneybookers"],
    ["mg", "gram", "moneygram"],
    ["mp", "pak", "moneypaks", "moneypak"],
    ["neteller"],
    ["okpay"],
    ["omc", "omcv2", "omcs", "open metaverse currency", "open metaverse"],
    ["omnicoins", "omnicoin"],
    ["payoneer"],
    ["pf", "perfectmoney", "perfect money"],
    ["pokerstars"],
    ["pp", "pay pal", "pay-pal", "paypal"],
    ["protoshares"],
    ["psc", "paysafe", "paysafecards", "paysafecard", "pay safe card"],
    ["pz", "payza"],
    ["skrill"],
    ["solid", "solidtrustpay"],
    ["starbucks"],
    ["steam"],
    ["stp"],
    ["ukash"],
    ["venmo"],
    ["wdc", "world coins", "worldcoins"],
    ["wmz", "webmoney", "web money"],
    ["wu", "w.u", "western", "union", "western union"],
    ["zetacoin"],
]
currency_dict = {}
fill_dict_with_lists(currency_dict, __currency__)

__trading__ = [
    ['h', 'got', 'have'],
    ['n', 'w', 'need', 'want'],
    [":"],
    ["for", 'to', '>'],
    ['<'],
]
trading_dict = {}
fill_dict_with_lists(trading_dict, __trading__)

__common__ = [
    ['i', 'my', 'me'],
    ['u', 'you', 'your'],
    ["go"],
    ["exchange"],
    ["can"],
    ["with"],
    ["do"],
    ["will"],
    ["or"],
    ["this"],
    ["a"],
    ["if"],
    ["and"],
]
common_dict = {}
fill_dict_with_lists(common_dict, __common__)

def map_word(word):
    if word.lower() in currency_dict:
        return CURRENCY
    elif word.lower() in trading_dict:
        return trading_dict[word.lower()]
    elif word.lower() in common_dict:
        return common_dict[word.lower()]
    elif pattern_numbers_re.match(word):
        return NUMBER
    else:
        return UNK

def get_type_and_canon(pos, text):
    token = text[pos[0]][pos[1]]
    word_type = map_word(token)
    
    canon = token.lower()
    if canon.lower() in currency_dict:
        canon = currency_dict[canon]

    # Consider one word in multi-word currency case
    token_prev = UNK
    if pos[1] > 0:
        token_prev = text[pos[0]][pos[1] - 1]
    pair0 = token_prev +" "+ token
    if pair0.lower() in currency_dict:
        canon = currency_dict[pair0.lower()]
        word_type = CURRENCY

    token_next = UNK
    if len(text[pos[0]]) > pos[1] + 1:
        token_next = text[pos[0]][pos[1] + 1]
    pair1 = token +" "+ token_next
    if pair1.lower() in currency_dict:
        canon = currency_dict[pair1.lower()]
        word_type = CURRENCY

    return (word_type, canon)

