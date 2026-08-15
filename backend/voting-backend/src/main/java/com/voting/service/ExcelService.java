package com.voting.service;

import com.voting.common.BizException;
import com.voting.entity.Candidate;
import com.voting.entity.Department;
import com.voting.entity.ThemeCategory;
import com.voting.entity.ThemeCustomField;
import com.voting.entity.VoteTheme;
import com.voting.mapper.BallotMapper;
import com.voting.mapper.CandidateMapper;
import com.voting.mapper.DepartmentMapper;
import com.voting.mapper.ThemeCategoryMapper;
import com.voting.mapper.ThemeCustomFieldMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * 候选人 Excel 模板导入导出 (POI, 动态列 = 名称+所属部门+分类+自定义字段)
 */
@Service
public class ExcelService {

    private final ThemeCustomFieldMapper fieldMapper;
    private final ThemeCategoryMapper categoryMapper;
    private final DepartmentMapper departmentMapper;
    private final CandidateMapper candidateMapper;
    private final BallotMapper ballotMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExcelService(ThemeCustomFieldMapper fieldMapper, ThemeCategoryMapper categoryMapper,
                        DepartmentMapper departmentMapper, CandidateMapper candidateMapper,
                        BallotMapper ballotMapper) {
        this.fieldMapper = fieldMapper;
        this.categoryMapper = categoryMapper;
        this.departmentMapper = departmentMapper;
        this.candidateMapper = candidateMapper;
        this.ballotMapper = ballotMapper;
    }

    /** 导出模板: 列 = 排序 + 分类 + 所属部门 + 候选项 + 自定义字段 (与前端表格列顺序一致) */
    public void exportTemplate(Long themeId, OutputStream out) throws Exception {
        List<ThemeCustomField> fields = fieldMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThemeCustomField>()
                        .eq(ThemeCustomField::getThemeId, themeId)
                        .orderByAsc(ThemeCustomField::getSort));
        List<ThemeCategory> cats = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, themeId)
                        .orderByAsc(ThemeCategory::getSort));
        List<Department> depts = departmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSort));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("候选人模板");
            // 表头: 排序 | [分类] | 所属部门 | 候选项 | 自定义字段...
            List<String> headers = new ArrayList<>();
            headers.add("排序");
            if (!cats.isEmpty()) {
                headers.add("分类");
            }
            headers.add("所属部门*");
            headers.add("候选项*");
            for (ThemeCustomField f : fields) {
                headers.add(f.getFieldLabel() + (Boolean.TRUE.equals(f.getIsRequired()) ? "*" : ""));
            }
            Row headRow = sheet.createRow(0);
            CellStyle headStyle = wb.createCellStyle();
            Font headFont = wb.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headStyle);
                sheet.setColumnWidth(i, 16 * 256);
            }
            // 示例行
            Row demo = sheet.createRow(1);
            int demoCol = 0;
            demo.createCell(demoCol++).setCellValue(1);                    // 排序
            if (!cats.isEmpty()) {
                demo.createCell(demoCol++).setCellValue(cats.get(0).getName()); // 分类
            }
            demo.createCell(demoCol++).setCellValue(depts.isEmpty() ? "" : depts.get(0).getName()); // 所属部门
            demo.createCell(demoCol++).setCellValue("示例候选项");            // 候选项
            for (ThemeCustomField f : fields) {
                demo.createCell(demoCol++).setCellValue("示例值");
            }
            // 所属部门下拉 (隐藏Sheet引用, 支持任意数量部门; 不用内联公式列表因255字符限制)
            if (!depts.isEmpty()) {
                Sheet deptSheet = wb.createSheet("部门列表");
                for (int i = 0; i < depts.size(); i++) {
                    deptSheet.createRow(i).createCell(0).setCellValue(depts.get(i).getName());
                }
                wb.setSheetHidden(wb.getSheetIndex(deptSheet), true);

                DataValidationHelper helper = sheet.getDataValidationHelper();
                // 引用隐藏Sheet的部门名区域(中文Sheet名需单引号包裹)
                DataValidationConstraint constraint =
                        helper.createFormulaListConstraint("'部门列表'!$A$1:$A$" + depts.size());
                // 所属部门列: 有分类时在 index 2, 无分类时在 index 1
                int deptCol = cats.isEmpty() ? 1 : 2;
                CellRangeAddressList range = new CellRangeAddressList(1, 1000, deptCol, deptCol);
                DataValidation validation = helper.createValidation(constraint, range);
                validation.setShowErrorBox(true);
                sheet.addValidationData(validation);
            }
            wb.write(out);
        }
    }

    /**
     * 导出得票统计: 行 = 候选(含分类), 列 = 排序 + 分类 + 候选项 + 所属部门 + 第N轮... + 总得票 + 是否当选
     */
    public void exportVoteStats(Long themeId, OutputStream out) throws Exception {
        List<ThemeCategory> cats = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, themeId)
                        .orderByAsc(ThemeCategory::getSort));
        List<Candidate> cands = candidateMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .orderByAsc(Candidate::getSort).orderByAsc(Candidate::getId));
        // 分类名映射
        Map<Long, String> catName = new HashMap<>();
        for (ThemeCategory c : cats) {
            catName.put(c.getId(), c.getName());
        }
        // 部门名映射
        Map<Long, String> deptName = new HashMap<>();
        for (Department d : departmentMapper.selectList(null)) {
            deptName.put(d.getId(), d.getName());
        }
        // 每轮每候选得票
        List<Map<String, Object>> rows = ballotMapper.countVotesByAllRounds(themeId);
        // 轮次集合(去重升序)
        TreeSet<Integer> rounds = new TreeSet<>();
        for (Map<String, Object> r : rows) {
            rounds.add(((Number) r.get("round")).intValue());
        }
        // candidateId-round -> cnt
        Map<String, Integer> cntMap = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Long cid = ((Number) r.get("candidateId")).longValue();
            Integer rd = ((Number) r.get("round")).intValue();
            cntMap.put(cid + "-" + rd, ((Number) r.get("cnt")).intValue());
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("得票统计");
            CellStyle headStyle = wb.createCellStyle();
            Font headFont = wb.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);
            CellStyle winnerStyle = wb.createCellStyle();
            Font winnerFont = wb.createFont();
            winnerFont.setBold(true);
            winnerFont.setColor(IndexedColors.RED.getIndex());
            winnerStyle.setFont(winnerFont);

            // 表头: 排序 | 分类 | 候选项 | 所属部门 | 第1轮 | 第2轮 ... | 总得票 | 是否当选
            List<String> headers = new ArrayList<>();
            headers.add("排序");
            headers.add("分类");
            headers.add("候选项");
            headers.add("所属部门");
            for (Integer rd : rounds) {
                headers.add("第" + rd + "轮");
            }
            headers.add("总得票");
            headers.add("是否当选");
            Row headRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = headRow.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headStyle);
                sheet.setColumnWidth(i, i <= 2 ? 14 * 256 : (i == 3 ? 16 * 256 : 8 * 256));
            }
            int r = 1;
            for (Candidate c : cands) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(c.getSort() == null ? 0 : c.getSort());
                row.createCell(1).setCellValue(c.getCategoryId() == null ? "" : catName.getOrDefault(c.getCategoryId(), ""));
                row.createCell(2).setCellValue(c.getName());
                row.createCell(3).setCellValue(c.getDepartmentId() == null ? "" : deptName.getOrDefault(c.getDepartmentId(), ""));
                int total = 0;
                int col = 4;
                for (Integer rd : rounds) {
                    int v = cntMap.getOrDefault(c.getId() + "-" + rd, 0);
                    row.createCell(col++).setCellValue(v);
                    total += v;
                }
                Cell totalCell = row.createCell(col++);
                totalCell.setCellValue(total);
                Cell winCell = row.createCell(col);
                boolean win = Boolean.TRUE.equals(c.getIsWinner());
                winCell.setCellValue(win ? "当选" : "");
                if (win) {
                    totalCell.setCellStyle(winnerStyle);
                    winCell.setCellStyle(winnerStyle);
                }
            }
            wb.write(out);
        }
    }

    /** 导入: 覆盖模式(先删除该主题全部候选项, 再插入 Excel 数据); 错误行跳过并汇总返回 */
    public Map<String, Object> importCandidates(Long themeId, MultipartFile file) throws Exception {
        List<ThemeCustomField> fields = fieldMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThemeCustomField>()
                        .eq(ThemeCustomField::getThemeId, themeId)
                        .orderByAsc(ThemeCustomField::getSort));
        List<ThemeCategory> cats = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, themeId)
                        .orderByAsc(ThemeCategory::getSort));
        Map<String, Department> deptMap = new HashMap<>();
        for (Department d : departmentMapper.selectList(null)) {
            deptMap.put(d.getName(), d);
        }
        Map<String, ThemeCategory> catMap = new HashMap<>();
        for (ThemeCategory c : cats) {
            catMap.put(c.getName(), c);
        }

        // 覆盖模式: 先删除该主题已有候选项
        candidateMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getThemeId, themeId));

        List<String> errors = new ArrayList<>();
        int success = 0;
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            int firstRow = sheet.getFirstRowNum() + 1; // 跳过表头
            for (int r = firstRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                // 列索引: 排序=0 | [分类] | 所属部门 | 候选项 | 自定义字段...
                int colIdx = 0;
                String sortStr = getCell(row, colIdx++);
                String catName = cats.isEmpty() ? null : getCell(row, colIdx++);
                String deptName = getCell(row, colIdx++);
                String name = getCell(row, colIdx++);
                if (name == null || name.isBlank()) continue; // 空行跳过

                try {
                    if (deptName == null || deptName.isBlank() || !deptMap.containsKey(deptName)) {
                        throw new BizException("所属部门不存在: " + deptName);
                    }
                    Candidate c = new Candidate();
                    c.setThemeId(themeId);
                    c.setName(name.trim());
                    c.setDepartmentId(deptMap.get(deptName).getId());
                    if (sortStr != null && !sortStr.isBlank()) {
                        try {
                            c.setSort(Integer.parseInt(sortStr.trim()));
                        } catch (NumberFormatException ignore) {
                            // 排序非数字时忽略
                        }
                    }
                    if (catName != null && !catName.isBlank()) {
                        ThemeCategory cat = catMap.get(catName);
                        if (cat == null) {
                            throw new BizException("分类不存在: " + catName);
                        }
                        c.setCategoryId(cat.getId());
                    }
                    // 自定义字段
                    Map<String, Object> extraMap = new LinkedHashMap<>();
                    for (ThemeCustomField f : fields) {
                        String val = getCell(row, colIdx++);
                        extraMap.put(f.getFieldLabel(), val == null ? "" : val);
                    }
                    if (!extraMap.isEmpty()) {
                        c.setExtra(objectMapper.writeValueAsString(extraMap));
                    }
                    // 排序: 优先用 Excel 排序列, 未填则按导入顺序
                    if (c.getSort() == null) {
                        c.setSort(success);
                    }
                    c.setIsWinner(false);
                    c.setRank(null);
                    c.setVotes(0);
                    candidateMapper.insert(c);
                    success++;
                } catch (Exception e) {
                    errors.add("第 " + (r + 1) + " 行: " + e.getMessage());
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("errors", errors);
        return result;
    }

    private String getCell(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            default: return null;
        }
    }
}
