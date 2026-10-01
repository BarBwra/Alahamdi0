"""
Regenerates the Arabic lang files.

Minecraft has no bidi or shaping engine, so plain Arabic renders as disconnected,
backwards letters. Every value below is baked into Arabic Presentation Forms-B
(U+FE70-FEFF, covered by the bundled unifont) and pre-reversed into visual order.
Because of that, never interpolate a number into one of these strings - the UI
draws label and value as separate aligned elements instead.

    pip install arabic-reshaper python-bidi && python genlang.py
"""
import json, os
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = "src/main/resources/assets/mlum/lang"
os.makedirs(ROOT, exist_ok=True)

def ar(s):
    return get_display(arabic_reshaper.reshape(s))

LANG = {
    "container.mlum.inventory":   ar("عدة الميدان"),

    "gui.mlum.header.vicinity":   ar("المحيط"),
    "gui.mlum.header.inventory":  ar("الحقيبة"),
    "gui.mlum.header.hotbar":     ar("الوصول السريع"),
    "gui.mlum.header.primary":    ar("السلاح الأساسي"),
    "gui.mlum.header.secondary":  ar("السلاح الثانوي"),
    "gui.mlum.header.status":     ar("الحالة"),

    "gui.mlum.vicinity.empty":    ar("لا توجد أغراض قريبة"),
    "gui.mlum.vicinity.more":     ar("أخرى"),
    "gui.mlum.vicinity.loot_all": ar("الكل"),
    "gui.mlum.vicinity.meters":   ar("م"),

    "gui.mlum.stat.health":       ar("الصحة"),
    "gui.mlum.stat.food":         ar("الطعام"),
    "gui.mlum.stat.armor":        ar("الدرع"),
    "gui.mlum.capacity":          ar("السعة"),
    "gui.mlum.slots":             ar("خانة"),

    "gui.mlum.gun.only":          ar("أسلحة نارية فقط"),
    "gui.mlum.quick.no_guns":     ar("لا أسلحة هنا"),

    "gui.mlum.slot.helmet":       ar("الخوذة"),
    "gui.mlum.slot.chest":        ar("الصدرية"),
    "gui.mlum.slot.legs":         ar("البنطال"),
    "gui.mlum.slot.boots":        ar("الحذاء"),
    "gui.mlum.slot.offhand":      ar("اليد الثانية"),

    "gui.mlum.nav.quest":         ar("المهام"),
    "gui.mlum.nav.inventory":     ar("الحقيبة"),
    "gui.mlum.nav.vehicle":       ar("المركبات"),

    "gui.mlum.quest.type.main":   ar("المهمة الأساسية"),
    "gui.mlum.quest.type.side":   ar("مهمة جانبية"),
    "gui.mlum.quest.active":      ar("المهام النشطة"),
    "gui.mlum.quest.completed":   ar("المهام المكتملة"),
    "gui.mlum.quest.details":     ar("التفاصيل"),
    "gui.mlum.quest.rewards":     ar("المكافآت"),
    "gui.mlum.quest.none":        ar("لا توجد مهام"),
    "gui.mlum.quest.select":      ar("اختر مهمة لعرض تفاصيلها"),
    "gui.mlum.quest.done":        ar("مكتملة"),
    "gui.mlum.quest.claim":       ar("استلام"),
    "gui.mlum.quest.claimed":     ar("تم الاستلام"),



    "gui.mlum.vehicle.list":      ar("المركبات"),
    "gui.mlum.vehicle.none":      ar("لا تملك أي مركبة"),
    "gui.mlum.vehicle.select":    ar("اختر مركبة"),
    "gui.mlum.vehicle.summon":    ar("استدعاء"),
    "gui.mlum.vehicle.missing":   ar("المود غير مثبت"),
    "gui.mlum.vehicle.no_preview": ar("لا يمكن عرض المجسم"),
    "gui.mlum.vehicle.cooldown":  ar("انتظر قليلا"),
    "gui.mlum.vehicle.out_of_stock": ar("لا يوجد رصيد"),
    "gui.mlum.vehicle.store":     ar("تخزين المركبة"),
    "gui.mlum.vehicle.lost":      ar("فقدت المركبة"),
    "gui.mlum.vehicle.kind_persistent": ar("دائمة"),
    "gui.mlum.vehicle.kind_consumable": ar("قابلة للاستهلاك"),

    "gui.mlum.vehicle.fail.not_owned":     ar("لا تملك هذه المركبة"),
    "gui.mlum.vehicle.fail.unknown_entity": ar("المود غير مثبت"),
    "gui.mlum.vehicle.fail.combat_locked": ar("ممنوع أثناء القتال"),
    "gui.mlum.vehicle.fail.cooldown":      ar("انتظر قليلا"),
    "gui.mlum.vehicle.fail.no_space":       ar("لا يوجد مكان كاف"),
    "gui.mlum.vehicle.fail.failed":         ar("فشل الاستدعاء"),
    "gui.mlum.vehicle.fail.wrong_dimension": ar("لا يمكن الاستدعاء في هذا العالم"),
    "gui.mlum.vehicle.fail.blocked_zone":   ar("منطقة محظورة"),
    "gui.mlum.vehicle.fail.already_out":    ar("لديك مركبة بالخارج"),
    "gui.mlum.vehicle.fail.ok":             ar("تم"),
}

for name in ("en_us.json", "ar_sa.json"):
    with open(f"{ROOT}/{name}", "w", encoding="utf-8") as fh:
        json.dump(LANG, fh, ensure_ascii=False, indent=4)
print(f"{len(LANG)} keys written to en_us.json and ar_sa.json")
