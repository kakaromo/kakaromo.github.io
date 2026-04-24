// com.samsung.move.metadata.util.MappedResultFlattener
// binmapper 가 돌려주는 MappedResult (중첩 struct/배열 트리) 를 dot-notation flat Map 으로 변환.
//
// 규칙:
//  - 중첩 struct: parent.child.grandchild
//  - 배열 원소: arr[0], arr[1], arr[0].field
//  - 단일 instance: structName 생략 (필드를 최상위로)
//  - 여러 instance (repeatAsArray): structName[0].field, structName[1].field
public final class MappedResultFlattener {

    private MappedResultFlattener() {}

    public static Map<String, Object> flatten(MappedResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (result == null || result.getInstances() == null || result.getInstances().isEmpty())
            return out;

        List<MappedResult.MappedInstance> instances = result.getInstances();
        if (instances.size() == 1) {
            // 단일 인스턴스 — prefix 없이 바로 필드부터
            walk(instances.get(0).getFields(), "", out);
        } else {
            // 여러 인스턴스 — structName[i].field 형태
            String base = result.getStructName() != null ? result.getStructName() : "instance";
            for (int i = 0; i < instances.size(); i++) {
                walk(instances.get(i).getFields(), base + "[" + i + "]", out);
            }
        }
        return out;
    }

    // 재귀적으로 트리 순회 — 리프 노드는 parsedValue, 중간 노드는 prefix 확장
    private static void walk(List<MappedField> fields, String prefix, Map<String, Object> out) {
        if (fields == null) return;
        for (MappedField f : fields) {
            String key = join(prefix, f.getFieldName());
            List<MappedField> children = f.getChildren();
            if (children == null || children.isEmpty()) {
                out.put(key, f.getParsedValue());   // Long/Double/String/byte[]/Object
            } else {
                walk(children, key, out);
            }
        }
    }

    // join("", "name")       → "name"
    // join("parent", "child") → "parent.child"
    // join("arr", "[0]")      → "arr[0]"     ← 배열 인덱스는 dot 없이
    private static String join(String prefix, String name) {
        if (name == null || name.isEmpty()) return prefix;
        if (prefix == null || prefix.isEmpty()) return name;
        if (name.startsWith("[")) return prefix + name;
        return prefix + "." + name;
    }
}
