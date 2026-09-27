package io.github.xfl2342.voiceassistant.widget

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 小组件布局的「控件白名单」检查。
 *
 * 桌面小组件不是普通的界面：启动器是把布局**照着画**一遍，只认带 `@RemoteView`
 * 标注的那几个控件。用了名单之外的控件（最容易踩的是拿 `View` 当分隔线），
 * 启动器会在解布局时直接放弃，桌面上只会显示一句「载入窗口小组件时出现问题」——
 * 而这个错误发生在启动器进程里，应用这边什么都看不到，很难查。
 *
 * 所以这里直接读布局文件，把用到的控件名对一遍名单。跑起来只是解析几个 XML，
 * 却能挡住一整类「装到手机上才发现空白」的问题。
 */
class WidgetLayoutTest {

    @Test
    fun `小组件布局里只出现启动器支持的控件`() {
        val layouts = widgetLayoutFiles()
        assertTrue("没找到小组件布局文件，检查一下测试的工作目录", layouts.isNotEmpty())

        layouts.forEach { file ->
            val unsupported = elementNames(file) - SUPPORTED_VIEWS
            assertTrue(
                "${file.name} 里用了小组件不支持的控件：$unsupported" +
                    "（启动器会拒绝整块布局，改用一个支持的控件，比如用 LinearLayout 画分隔线）",
                unsupported.isEmpty(),
            )
        }
    }

    /**
     * 启动器允许出现在小组件布局里的控件。
     *
     * 名单来自 RemoteViews 对 `@RemoteView` 的标注，`include` 不算控件但要放行
     * （它是布局包含标签，解完之后还是这些控件之一）。
     */
    private val SUPPORTED_VIEWS = setOf(
        "include",
        // 容器
        "LinearLayout",
        "RelativeLayout",
        "FrameLayout",
        "GridLayout",
        // 常用控件
        "TextView",
        "Button",
        "ImageButton",
        "ImageView",
        "ProgressBar",
        "Chronometer",
        "AnalogClock",
        // 滚动与分页
        "ViewFlipper",
        "ListView",
        "GridView",
        "StackView",
        "AdapterViewFlipper",
        "ViewStub",
    )

    private fun widgetLayoutFiles(): List<File> =
        layoutDirectory().listFiles { file -> file.name.startsWith("widget") && file.name.endsWith(".xml") }
            ?.sortedBy { it.name }
            .orEmpty()

    /** 单元测试的工作目录可能是工程根目录，也可能是 app 模块目录，两种都往上找一找。 */
    private fun layoutDirectory(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            listOf("app/src/main/res/layout", "src/main/res/layout").forEach { relative ->
                val candidate = File(directory, relative)
                if (candidate.isDirectory) return candidate
            }
            directory = directory.parentFile
        }
        fail("找不到 res/layout 目录")
        error("unreachable")
    }

    private fun elementNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val names = mutableSetOf<String>()

        fun walk(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                names += node.nodeName.substringAfterLast(':')
            }
            var child = node.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }

        walk(document.documentElement)
        return names
    }
}
