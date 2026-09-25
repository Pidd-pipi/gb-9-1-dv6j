import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Typography, Button, Spin, Alert, Space, Tag } from 'antd'
import { ArrowLeftOutlined, LockOutlined } from '@ant-design/icons'
import { columnApi } from '../api/column'
import type { Article, Column } from '../types'
import dayjs from 'dayjs'

const { Title, Paragraph, Text } = Typography

function ArticleReader() {
  const { columnId, articleId } = useParams<{ columnId: string; articleId: string }>()
  const navigate = useNavigate()
  const [article, setArticle] = useState<Article | null>(null)
  const [column, setColumn] = useState<Column | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (columnId && articleId) {
      loadArticle()
    }
  }, [columnId, articleId])

  const loadArticle = async () => {
    if (!columnId || !articleId) return
    setLoading(true)
    try {
      const [articleRes, columnRes] = await Promise.all([
        columnApi.getArticle(columnId, articleId),
        columnApi.getById(columnId),
      ])
      setArticle(articleRes.data?.data || articleRes.data)
      setColumn(columnRes.data?.data || columnRes.data)
    } catch (error) {
      console.error('Failed to load article:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading || !article) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const locked = article.readable === false
  const expiredAfter = article.lockReason === 'EXPIRED_AFTER'

  return (
    <div>
      <Button
        type="link"
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate(`/columns/${columnId}`)}
        style={{ paddingLeft: 0, marginBottom: 8 }}
      >
        返回{column?.title ? `《${column.title}》` : '专栏'}
      </Button>
      <Card>
        <Title level={2}>{article.title}</Title>
        <Space style={{ marginBottom: 24 }}>
          <Text type="secondary">{dayjs(article.createdAt).format('YYYY-MM-DD HH:mm')}</Text>
          {article.readable === true && <Tag color="green">可阅读</Tag>}
          {locked && (
            <Tag icon={<LockOutlined />} color={expiredAfter ? 'orange' : 'default'}>
              {expiredAfter ? '到期后更新' : '未订阅'}
            </Tag>
          )}
        </Space>

        {locked ? (
          <div>
            <Alert
              type={expiredAfter ? 'warning' : 'info'}
              showIcon
              style={{ marginBottom: 16 }}
              message={
                expiredAfter
                  ? '该文章发布于您的订阅到期之后，续费后即可阅读'
                  : '订阅专栏后即可阅读全文'
              }
              action={
                <Button
                  type="primary"
                  size="small"
                  onClick={() => navigate(`/columns/${columnId}?renew=1`)}
                >
                  {expiredAfter ? '立即续费' : '去订阅'}
                </Button>
              }
            />
            <Paragraph type="secondary">{article.summary || '暂无摘要'}</Paragraph>
          </div>
        ) : (
          <Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 16, lineHeight: 1.9 }}>
            {article.content}
          </Paragraph>
        )}
      </Card>
    </div>
  )
}

export default ArticleReader
